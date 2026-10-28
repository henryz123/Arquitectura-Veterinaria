import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_CLIENT_AURORA_EMAIL', 'AV_DEMO_CLIENT_AURORA_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const operationId = '00000000-0000-4000-8000-000000000406';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const preparation = await browser.newContext();
  const preparationPage = await preparation.newPage();
  await loginStaff(preparationPage);
  const service = (await browserFetch(preparationPage, '/api/v1/schedule/services')).body?.data
    ?.find((item) => item.serviceType === 'CLINICA' && item.status === 'ACTIVO' && item.published);
  const professional = (await browserFetch(preparationPage, '/api/v1/schedule/assignable-professionals')).body?.data
    ?.find((item) => item.roles.includes('VETERINARIO'));
  assert.ok(service?.id && professional?.principalId, 'the blocks/no-show E2E needs an active clinical service and veterinarian');
  await preparation.close();

  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await clientPage.addInitScript((fixedId) => { crypto.randomUUID = () => fixedId; }, operationId);
  await loginClient(clientPage);
  let request = (await browserFetch(clientPage, '/api/v1/client/schedule/requests')).body?.data
    ?.find((item) => item.idempotencyKey === operationId);
  if (request?.status === 'PENDIENTE' && request.requestedStartsAt !== '2031-01-06T15:30:00Z') {
    const cancellation = clientPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/client/schedule/requests/${request.id}/cancellation`));
    await clientPage.locator(`[data-request-id="${request.id}"]`).getByRole('button', { name: 'Cancelar solicitud' }).click();
    assert.equal((await cancellation).status(), 200, 'an obsolete synthetic pending fixture must be cancelled through the visible CLIENT action');
    request = null;
  }
  if (!request) {
    const pet = (await browserFetch(clientPage, '/api/v1/client/pets')).body?.data?.find((item) => item.status === 'ACTIVA');
    assert.ok(pet?.id, 'the synthetic CLIENT needs an approved active pet');
    const created = clientPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/schedule/requests');
    await clientPage.locator('select[name="schedulePet"]').selectOption(pet.id);
    await clientPage.locator('select[name="scheduleService"]').selectOption(service.id);
    await clientPage.locator('input[name="scheduleDateTime"]').fill('2031-01-06T10:30');
    await clientPage.locator('input[name="scheduleObservation"]').fill('Fixture sintético de ausencia B03.');
    await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
    assert.equal((await created).status(), 201, 'CLIENT must persist the no-show fixture through the visible form');
    request = (await (await created).json()).data;
  }
  assert.ok(request?.id, 'the fixed CLIENT request must exist');
  await clientContext.close();

  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage);
  await staffPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  await staffPage.locator('#assignable-professional').selectOption(professional.principalId);
  let current = (await browserFetch(staffPage, '/api/v1/schedule/requests')).body?.data?.find((item) => item.id === request.id);
  if (current?.status === 'PENDIENTE') {
    const row = staffPage.locator(`[data-request-id="${request.id}"]`);
    const confirmation = staffPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/schedule/requests/${request.id}/confirmation`));
    await row.getByRole('button', { name: 'Comprobar y confirmar' }).click();
    assert.equal((await confirmation).status(), 200, 'staff must confirm the synthetic appointment before a no-show');
    current = (await browserFetch(staffPage, '/api/v1/schedule/requests')).body?.data?.find((item) => item.id === request.id);
  }
  assert.ok(['CONFIRMADA', 'INASISTENCIA'].includes(current?.status),
    'the fixture must reach CONFIRMADA or retain its idempotent INASISTENCIA state');

  if (current?.status === 'CONFIRMADA') {
    const row = staffPage.locator(`[data-request-id="${request.id}"]`);
    await row.locator('input[id^="no-show-reason-"]').fill('Inasistencia registrada en recorrido E2E.');
    const noShow = staffPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/schedule/requests/${request.id}/no-show`));
    await row.getByRole('button', { name: 'Marcar inasistencia' }).click();
    assert.equal((await noShow).status(), 200, 'staff must register the no-show through the visible action');
  } else {
    assert.equal(current?.status, 'INASISTENCIA', 'the synthetic request must be confirmed or retain its idempotent no-show state');
  }

  await staffPage.getByText('Inasistencia registrada', { exact: true }).waitFor({ state: 'visible' });
  const existingBlocks = (await browserFetch(staffPage, '/api/v1/schedule/blocks')).body?.data ?? [];
  if (!existingBlocks.some((item) => item.blockScope === 'PROFESIONAL' && item.professionalPrincipalId === professional.principalId
    && item.reason === 'Ausencia excepcional registrada en E2E.')) {
    await staffPage.locator('#block-scope').selectOption('PROFESIONAL');
    await staffPage.locator('#block-professional').selectOption(professional.principalId);
    await staffPage.locator('#block-start').fill('2031-01-06T11:30');
    await staffPage.locator('#block-end').fill('2031-01-06T12:00');
    await staffPage.locator('#block-reason').fill('Ausencia excepcional registrada en E2E.');
    const blockCreated = staffPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/blocks');
    await staffPage.getByRole('button', { name: 'Guardar bloqueo' }).click();
    assert.equal((await blockCreated).status(), 201, 'staff must persist an exceptional block through the visible form');
  }

  const finalRequest = (await browserFetch(staffPage, '/api/v1/schedule/requests')).body?.data?.find((item) => item.id === request.id);
  const blocks = (await browserFetch(staffPage, '/api/v1/schedule/blocks')).body?.data ?? [];
  assert.equal(finalRequest?.status, 'INASISTENCIA', 'the completed staff action must release the request to INASISTENCIA');
  assert.ok(blocks.some((item) => item.blockScope === 'PROFESIONAL' && item.professionalPrincipalId === professional.principalId
    && item.reason === 'Ausencia excepcional registrada en E2E.'), 'the tenant-local professional block must persist');
  const history = (await browserFetch(staffPage, '/api/v1/schedule/changes')).body?.data ?? [];
  assert.ok(history.some((item) => item.requestId === request.id && item.actionCode === 'INASISTENCIA_REGISTRADA'),
    'the administrative history must preserve the no-show without identity fields');
  await staffContext.close();
  process.stdout.write('B03_BLOCKS_NO_SHOW_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
}

async function loginClient(page) {
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
}

async function browserFetch(page, path) {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, path);
}
