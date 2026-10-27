import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_CLIENT_AURORA_EMAIL', 'AV_DEMO_CLIENT_AURORA_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const operationId = '00000000-0000-4000-8000-000000000401';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage);
  const service = (await browserFetch(staffPage, '/api/v1/schedule/services')).body?.data
    ?.find((item) => item.serviceType === 'CLINICA' && item.status === 'ACTIVO' && item.published);
  const professional = (await browserFetch(staffPage, '/api/v1/schedule/assignable-professionals')).body?.data
    ?.find((item) => item.roles.includes('VETERINARIO'));
  assert.ok(service?.id && professional?.principalId, 'the cancellation fixture needs an active clinical service and veterinarian');
  await staffContext.close();

  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await clientPage.addInitScript((fixedId) => { crypto.randomUUID = () => fixedId; }, operationId);
  await loginClient(clientPage);
  let request = (await browserFetch(clientPage, '/api/v1/client/schedule/requests')).body?.data
    ?.find((item) => item.idempotencyKey === operationId);
  if (!request) {
    const pet = (await browserFetch(clientPage, '/api/v1/client/pets')).body?.data?.find((item) => item.status === 'ACTIVA');
    assert.ok(pet?.id, 'the synthetic client needs an approved active pet');
    const created = clientPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/schedule/requests');
    await clientPage.locator('select[name="schedulePet"]').selectOption(pet.id);
    await clientPage.locator('select[name="scheduleService"]').selectOption(service.id);
    await clientPage.locator('input[name="scheduleDateTime"]').fill('2031-01-06T10:30');
    await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
    assert.equal((await created).status(), 201, 'CLIENT must create the cancellation fixture through the visible form');
    request = (await (await created).json()).data;
  }
  assert.ok(request?.id, 'the idempotent cancellation fixture must exist');
  await clientContext.close();

  if (request.status === 'PENDIENTE') {
    const confirmContext = await browser.newContext();
    const confirmPage = await confirmContext.newPage();
    await loginStaff(confirmPage);
    await confirmPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
    await confirmPage.locator('#assignable-professional').selectOption(professional.principalId);
    const row = confirmPage.locator(`[data-request-id="${request.id}"]`);
    const response = confirmPage.waitForResponse((item) => item.request().method() === 'POST'
      && new URL(item.url()).pathname.endsWith(`/schedule/requests/${request.id}/confirmation`));
    await row.getByRole('button', { name: 'Comprobar y confirmar' }).click();
    assert.equal((await response).status(), 200, 'the cancellation fixture must first be confirmed by staff');
    await confirmContext.close();
    request = { ...request, status: 'CONFIRMADA' };
  }

  if (request.status === 'CONFIRMADA') {
    const cancellationContext = await browser.newContext();
    const cancellationPage = await cancellationContext.newPage();
    await loginClient(cancellationPage);
    const response = cancellationPage.waitForResponse((item) => item.request().method() === 'POST'
      && new URL(item.url()).pathname.endsWith(`/client/schedule/requests/${request.id}/cancellation`));
    await cancellationPage.locator(`[data-request-id="${request.id}"]`).getByRole('button', { name: 'Solicitar cancelacion' }).click();
    assert.equal((await response).status(), 200, 'CLIENT may only request cancellation of a confirmed appointment');
    await cancellationContext.close();
  }

  const resolveContext = await browser.newContext();
  const resolvePage = await resolveContext.newPage();
  await loginStaff(resolvePage);
  await resolvePage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  const resolutionRow = resolvePage.locator(`[data-request-id="${request.id}"]`);
  const currentStatus = await browserFetch(resolvePage, '/api/v1/schedule/requests');
  const current = currentStatus.body?.data?.find((item) => item.id === request.id);
  if (current?.status === 'CANCELACION_SOLICITADA') {
    await resolutionRow.locator('input[id^="cancellation-reason-"]').fill('Cancelacion aprobada por recepcion.');
    const response = resolvePage.waitForResponse((item) => item.request().method() === 'POST'
      && new URL(item.url()).pathname.endsWith(`/schedule/requests/${request.id}/cancellation-resolution`));
    await resolutionRow.getByRole('button', { name: 'Aprobar cancelacion' }).click();
    assert.equal((await response).status(), 200, 'staff approval must release the confirmed appointment');
  }
  await resolveContext.close();

  const verificationContext = await browser.newContext();
  const verificationPage = await verificationContext.newPage();
  await loginClient(verificationPage);
  const finalRequests = await browserFetch(verificationPage, '/api/v1/client/schedule/requests');
  assert.equal(finalRequests.body?.data?.find((item) => item.id === request.id)?.status, 'CANCELADA',
    'CLIENT must observe the staff-approved cancellation without retaining a confirmed slot');
  await verificationContext.close();
  process.stdout.write('B03_CANCELLATION_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
}

async function loginClient(page) {
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
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
