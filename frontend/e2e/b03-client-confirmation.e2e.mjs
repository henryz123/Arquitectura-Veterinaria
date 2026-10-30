import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_BOSQUE_PASSWORD',
  'AV_DEMO_CLIENT_AURORA_EMAIL',
  'AV_DEMO_CLIENT_AURORA_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage, 'veterinaria-aurora', process.env.AV_DEMO_AURORA_PASSWORD);
  await staffPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  await staffPage.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();
  const staffServices = await browserFetch(staffPage, '/api/v1/schedule/services');
  const service = staffServices.body?.data?.find((item) => item.serviceType === 'CLINICA' && item.status === 'ACTIVO' && item.published);
  assert.ok(service?.id, 'the B03 E2E needs an active published clinical service');
  const professionals = await browserFetch(staffPage, '/api/v1/schedule/assignable-professionals');
  const professional = professionals.body?.data?.find((item) => item.roles.includes('VETERINARIO'));
  assert.ok(professional?.principalId, 'the B03 E2E needs an active veterinarian');
  await staffContext.close();

  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
	await clientPage.addInitScript(() => {
		const ids = ['00000000-0000-4000-8000-000000000303', '00000000-0000-4000-8000-000000000304'];
		const fallback = crypto.randomUUID.bind(crypto);
		crypto.randomUUID = () => ids.shift() ?? fallback();
	});
  await loginClient(clientPage);
  await clientPage.getByRole('button', { name: 'Solicitar cita' }).click();
  await clientPage.getByRole('heading', { name: 'Solicitar cita' }).waitFor();
  const pets = await browserFetch(clientPage, '/api/v1/client/pets');
  const pet = pets.body?.data?.find((item) => item.status === 'ACTIVA');
  assert.ok(pet?.id, 'the synthetic client must have an approved current pet before requesting a visit');
  const clientServices = await browserFetch(clientPage, '/api/v1/client/schedule/services');
  assert.ok(clientServices.body?.data?.some((item) => item.id === service.id), 'the client must receive the published tenant service');

  const firstStartsAt = farFutureMonday(0);
  const firstResponse = clientPage.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/client/schedule/requests');
  await clientPage.locator('select[name="schedulePet"]').selectOption(pet.id);
  await clientPage.locator('select[name="scheduleService"]').selectOption(service.id);
  await clientPage.locator('input[name="scheduleDateTime"]').fill(firstStartsAt);
  await clientPage.locator('input[name="scheduleObservation"]').fill('Solicitud B03 sintética desde navegador');
  await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
  const firstCreated = await firstResponse;
  assert.equal(firstCreated.status(), 201, 'a CLIENT request must persist through the visible form');
  const firstRequest = (await firstCreated.json()).data;
  assert.ok(['PENDIENTE', 'CONFIRMADA'].includes(firstRequest.status), 'the idempotent client request must retain a valid schedule state');

  await clientPage.getByRole('button', { name: 'Solicitar cita' }).click();
  await clientPage.getByRole('heading', { name: 'Solicitar cita' }).waitFor();
  const secondStartsAt = firstStartsAt;
  const secondResponse = clientPage.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/client/schedule/requests');
  await clientPage.locator('select[name="schedulePet"]').selectOption(pet.id);
  await clientPage.locator('select[name="scheduleService"]').selectOption(service.id);
  await clientPage.locator('input[name="scheduleDateTime"]').fill(secondStartsAt);
  await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
  const secondCreated = await secondResponse;
  assert.equal(secondCreated.status(), 201, 'a second pending proposal does not consume the same slot');
  const secondRequest = (await secondCreated.json()).data;
  await logoutClient(clientPage);
  await clientContext.close();

  const confirmerContext = await browser.newContext();
  const confirmerPage = await confirmerContext.newPage();
  await loginStaff(confirmerPage, 'veterinaria-aurora', process.env.AV_DEMO_AURORA_PASSWORD);
  await confirmerPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  await confirmerPage.locator('#assignable-professional').selectOption(professional.principalId);
  const firstRow = confirmerPage.locator(`[data-request-id="${firstRequest.id}"]`);
  await firstRow.waitFor();
  if (firstRequest.status === 'PENDIENTE') {
    const firstConfirmed = confirmerPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/schedule/requests/${firstRequest.id}/confirmation`));
    await firstRow.getByRole('button', { name: 'Comprobar y confirmar' }).click();
    assert.equal((await firstConfirmed).status(), 200, 'staff confirmation must create exactly one reservation');
  }

  const secondRow = confirmerPage.locator(`[data-request-id="${secondRequest.id}"]`);
  await secondRow.waitFor();
  await secondRow.getByRole('button', { name: 'Comprobar y confirmar' }).click();
  await confirmerPage.getByText(/El intervalo ya no es confirmable/).waitFor();
  await confirmerContext.close();

  const bosqueContext = await browser.newContext();
  const bosquePage = await bosqueContext.newPage();
  await loginStaff(bosquePage, 'veterinaria-bosque', process.env.AV_DEMO_BOSQUE_PASSWORD);
  const bosqueRequests = await browserFetch(bosquePage, '/api/v1/schedule/requests');
  assert.equal(bosqueRequests.status, 200, 'Bosque staff must read only its own schedule requests');
  assert.equal((bosqueRequests.body?.data ?? []).some((item) => item.id === firstRequest.id), false,
    'Bosque must not see an Aurora request identifier');
  await bosqueContext.close();

  const verificationContext = await browser.newContext();
  const verificationPage = await verificationContext.newPage();
  await loginClient(verificationPage);
  const ownRequests = await browserFetch(verificationPage, '/api/v1/client/schedule/requests');
  assert.equal(ownRequests.status, 200, 'the client must read only its own schedule requests');
  assert.equal(ownRequests.body?.data?.find((item) => item.id === firstRequest.id)?.status, 'CONFIRMADA',
    'the client must observe the server-confirmed state after a manual confirmation');
  await verificationContext.close();

  process.stdout.write('B03_CLIENT_CONFIRMATION_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

function farFutureMonday(minuteOffset) {
  const date = new Date(2031, 0, 13, 9, minuteOffset, 0, 0);
  while (date.getDay() !== 1) date.setDate(date.getDate() + 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

async function loginStaff(page, tenantSlug, password) {
  await page.goto(`${baseUrl}/login/${tenantSlug}`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(password);
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

async function logoutClient(page) {
  await page.getByRole('button', { name: 'Cerrar sesion' }).click();
  await page.waitForURL(`${baseUrl}/client/login/veterinaria-aurora`);
}

async function browserFetch(page, path) {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, path);
}
