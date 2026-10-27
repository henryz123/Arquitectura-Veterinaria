import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_CLIENT_AURORA_EMAIL', 'AV_DEMO_CLIENT_AURORA_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const operationId = '00000000-0000-4000-8000-000000000403';
const proposedLocalDateTime = '2031-01-13T11:30';
const proposedInstant = '2031-01-13T16:30:00Z';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const preparation = await browser.newContext();
  const preparationPage = await preparation.newPage();
  await loginStaff(preparationPage);
  const service = (await browserFetch(preparationPage, '/api/v1/schedule/services')).body?.data
    ?.find((item) => item.serviceType === 'CLINICA' && item.status === 'ACTIVO' && item.published);
  const professional = (await browserFetch(preparationPage, '/api/v1/schedule/assignable-professionals')).body?.data
    ?.find((item) => item.roles.includes('VETERINARIO'));
  assert.ok(service?.id && professional?.principalId, 'the reprogramming E2E needs an active clinical service and veterinarian');
  await preparation.close();

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
    await clientPage.locator('input[name="scheduleDateTime"]').fill('2031-01-13T09:30');
    await clientPage.locator('input[name="scheduleObservation"]').fill('Fixture sintético de reprogramación B03.');
    await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
    assert.equal((await created).status(), 201, 'CLIENT must persist the reprogramming fixture through the visible form');
    request = (await (await created).json()).data;
  }
  assert.ok(request?.id, 'the fixed CLIENT request must exist');
  await clientContext.close();

  request = await confirmIfPending(request, professional.principalId);
  if (request.status === 'CONFIRMADA' && request.requestedStartsAt !== proposedInstant) {
    const proposalContext = await browser.newContext();
    const proposalPage = await proposalContext.newPage();
    await loginStaff(proposalPage);
    await proposalPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
    await proposalPage.locator('#assignable-professional').selectOption(professional.principalId);
    const row = proposalPage.locator(`[data-request-id="${request.id}"]`);
    await row.locator('input[id^="proposal-starts-at-"]').fill(proposedLocalDateTime);
    await row.locator('input[id^="proposal-reason-"]').fill('Cambio de horario solicitado por recepción.');
    const response = proposalPage.waitForResponse((item) => item.request().method() === 'POST'
      && new URL(item.url()).pathname.endsWith(`/schedule/requests/${request.id}/proposals`));
    await row.getByRole('button', { name: 'Enviar propuesta' }).click();
    assert.equal((await response).status(), 201, 'staff must send a time-changing proposal without consuming its new slot');
    const current = await browserFetch(proposalPage, '/api/v1/schedule/requests');
    request = { ...current.body?.data?.find((item) => item.id === request.id), originalStartsAt: request.originalStartsAt ?? request.requestedStartsAt };
    await proposalContext.close();
  }

  request = await clientDecisionIfPending(request);
  request = await confirmIfPending(request, professional.principalId);

  const verification = await browser.newContext();
  const verificationPage = await verification.newPage();
  await loginClient(verificationPage);
  const finalRequests = await browserFetch(verificationPage, '/api/v1/client/schedule/requests');
  const finalRequest = finalRequests.body?.data?.find((item) => item.id === request.id);
  assert.equal(finalRequest?.status, 'CONFIRMADA', 'accepted reprogramming must finish as exactly one confirmed request');
  assert.equal(finalRequest?.requestedStartsAt, proposedInstant,
    'the final request must retain the accepted alternative, not the original interval');
  const proposals = await browserFetch(verificationPage, '/api/v1/client/schedule/proposals');
  assert.equal((proposals.body?.data ?? []).some((item) => item.requestId === request.id), false,
    'the CLIENT must not retain an actionable proposal after it is decided');
  await verification.close();
  process.stdout.write('B03_REPROGRAMMING_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function confirmIfPending(request, professionalPrincipalId) {
  if (request.status !== 'PENDIENTE' && request.status !== 'PENDIENTE_REPROGRAMACION') return request;
  const context = await browser.newContext();
  const page = await context.newPage();
  await loginStaff(page);
  await page.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  await page.locator('#assignable-professional').selectOption(professionalPrincipalId);
  const row = page.locator(`[data-request-id="${request.id}"]`);
  const actionName = request.status === 'PENDIENTE_REPROGRAMACION' ? 'Revalidar y confirmar cambio' : 'Comprobar y confirmar';
  const response = page.waitForResponse((item) => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith(`/schedule/requests/${request.id}/confirmation`));
  await row.getByRole('button', { name: actionName }).click();
  assert.equal((await response).status(), 200, 'staff must confirm the pending or accepted reprogramming transition');
  const current = await browserFetch(page, '/api/v1/schedule/requests');
  await context.close();
  const resolved = current.body?.data?.find((item) => item.id === request.id);
  return { ...resolved, originalStartsAt: request.originalStartsAt ?? request.requestedStartsAt };
}

async function clientDecisionIfPending(request) {
  if (request.status !== 'REPROGRAMACION_PROPUESTA') return request;
  const context = await browser.newContext();
  const page = await context.newPage();
  await loginClient(page);
  const proposals = await browserFetch(page, '/api/v1/client/schedule/proposals');
  const proposal = proposals.body?.data?.find((item) => item.requestId === request.id);
  assert.ok(proposal?.id, 'CLIENT must receive its own pending alternative');
  assert.equal(Object.hasOwn(proposal, 'proposedProfessionalPrincipalId'), false,
    'the CLIENT proposal projection must not expose the staff professional identifier');
  const response = page.waitForResponse((item) => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith(`/client/schedule/proposals/${proposal.id}/decision`));
  await page.locator(`[data-request-id="${request.id}"]`).getByRole('button', { name: 'Aceptar alternativa' }).click();
  assert.equal((await response).status(), 200, 'CLIENT acceptance must be a protected visible action');
  const current = await browserFetch(page, '/api/v1/client/schedule/requests');
  await context.close();
  const resolved = current.body?.data?.find((item) => item.id === request.id);
  return { ...resolved, originalStartsAt: request.requestedStartsAt };
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
