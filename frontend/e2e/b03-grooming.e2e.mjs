import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_CLIENT_AURORA_EMAIL',
  'AV_DEMO_CLIENT_AURORA_PASSWORD',
  'AV_DEMO_GROOMER_AURORA_EMAIL',
  'AV_DEMO_GROOMER_AURORA_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const administratorEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const operationId = '00000000-0000-4000-8000-000000000404';
const requestedLocalDateTime = '2031-01-20T13:00';
const groomingServiceFixture = {
  serviceType: 'GROOMING',
  name: 'B03 Grooming E2E',
  durationMinutes: 30,
  referencePrice: 35,
};
const screenshotPath = process.env.AV_DEMO_SCREENSHOT_PATH
  ? resolve(process.env.AV_DEMO_SCREENSHOT_PATH)
  : null;
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const administration = await browser.newContext();
  const administrationPage = await administration.newPage();
  await loginStaff(administrationPage, administratorEmail, process.env.AV_DEMO_AURORA_PASSWORD);
  await administrationPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
  await administrationPage.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();
  let groomingService = (await browserFetch(administrationPage, '/api/v1/schedule/services')).body?.data
    ?.find((item) => item.name === groomingServiceFixture.name);
  if (!groomingService) {
    const created = administrationPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/services');
    await administrationPage.locator('#service-type').selectOption(groomingServiceFixture.serviceType);
    await administrationPage.locator('#service-name').fill(groomingServiceFixture.name);
    await administrationPage.locator('#service-duration').fill(String(groomingServiceFixture.durationMinutes));
    await administrationPage.locator('#service-price').fill(String(groomingServiceFixture.referencePrice));
    await administrationPage.getByRole('button', { name: 'Guardar servicio' }).click();
    const response = await created;
    assert.equal(response.status(), 201, 'staff must persist the grooming fixture through the visible service form');
    groomingService = (await response.json()).data;
  }
  assert.equal(groomingService.serviceType, 'GROOMING', 'the grooming fixture must retain its service type');
  assert.equal(groomingService.status, 'ACTIVO', 'the grooming fixture must be active');
  assert.equal(groomingService.published, true, 'the grooming fixture must be available to the linked CLIENT');
  const groomer = (await browserFetch(administrationPage, '/api/v1/staff-accounts')).body?.data
    ?.find((item) => item.email === process.env.AV_DEMO_GROOMER_AURORA_EMAIL
      && item.status === 'ACTIVA' && item.roles.includes('GROOMING'));
  assert.ok(groomingService?.id, 'the grooming E2E needs an active published GROOMING service');
  assert.ok(groomer?.principalId, 'the configured grooming account must be active and retain the GROOMING role');
  const assignable = (await browserFetch(administrationPage, '/api/v1/schedule/assignable-professionals')).body?.data
    ?.find((item) => item.principalId === groomer.principalId && item.roles.includes('GROOMING'));
  assert.ok(assignable, 'the configured grooming account must be assignable before confirmation');
  const operatingHours = await browserFetch(administrationPage, '/api/v1/schedule/operating-hours');
  if (!operatingHours.body?.data?.some((item) => item.weekday === 1 && item.startsAtLocal === '08:00:00'
    && item.endsAtLocal === '18:00:00')) {
    const created = administrationPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/operating-hours');
    await administrationPage.locator('#operating-weekday').selectOption('1');
    await administrationPage.locator('#operating-start').fill('08:00');
    await administrationPage.locator('#operating-end').fill('18:00');
    await administrationPage.getByRole('button', { name: 'Guardar horario' }).click();
    assert.equal((await created).status(), 201, 'staff must persist operating hours through the visible schedule form');
  }
  const professionalAvailability = await browserFetch(administrationPage, '/api/v1/schedule/professional-availability');
  if (!professionalAvailability.body?.data?.some((item) => item.professionalPrincipalId === groomer.principalId
    && item.weekday === 1 && item.startsAtLocal === '12:00:00' && item.endsAtLocal === '16:00:00')) {
    const created = administrationPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/professional-availability');
    await administrationPage.locator('#assignable-professional').selectOption(groomer.principalId);
    await administrationPage.locator('#professional-weekday').selectOption('1');
    await administrationPage.locator('#professional-start').fill('12:00');
    await administrationPage.locator('#professional-end').fill('16:00');
    await administrationPage.getByRole('button', { name: 'Guardar disponibilidad' }).click();
    assert.equal((await created).status(), 201, 'staff must persist Grooming availability through the visible schedule form');
  }
  await administration.close();

  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await clientPage.addInitScript((fixedId) => { crypto.randomUUID = () => fixedId; }, operationId);
  await loginClient(clientPage);
  let request = (await browserFetch(clientPage, '/api/v1/client/schedule/requests')).body?.data
    ?.find((item) => item.idempotencyKey === operationId);
  if (!request) {
    const pet = (await browserFetch(clientPage, '/api/v1/client/pets')).body?.data?.find((item) => item.status === 'ACTIVA');
    assert.ok(pet?.id, 'the synthetic CLIENT needs a current linked pet');
    const created = clientPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/schedule/requests');
    await clientPage.locator('select[name="schedulePet"]').selectOption(pet.id);
    await clientPage.locator('select[name="scheduleService"]').selectOption(groomingService.id);
    await clientPage.locator('input[name="scheduleDateTime"]').fill(requestedLocalDateTime);
    await clientPage.locator('input[name="scheduleObservation"]').fill('Fixture sintético de grooming B03.');
    await clientPage.getByRole('button', { name: 'Enviar solicitud' }).click();
    assert.equal((await created).status(), 201, 'CLIENT must persist the grooming request through the visible form');
    request = (await (await created).json()).data;
  }
  assert.ok(request?.id, 'the idempotent grooming request must exist');
  await clientContext.close();

  if (request.status === 'PENDIENTE') {
    const confirmationContext = await browser.newContext();
    const confirmationPage = await confirmationContext.newPage();
    await loginStaff(confirmationPage, administratorEmail, process.env.AV_DEMO_AURORA_PASSWORD);
    await confirmationPage.goto(`${baseUrl}/schedule`, { waitUntil: 'networkidle' });
    await confirmationPage.locator('#assignable-professional').selectOption(groomer.principalId);
    const row = confirmationPage.locator(`[data-request-id="${request.id}"]`);
    const confirmed = confirmationPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/schedule/requests/${request.id}/confirmation`));
    await row.getByRole('button', { name: 'Comprobar y confirmar' }).click();
    assert.equal((await confirmed).status(), 200, 'staff must confirm grooming only with its assigned available groomer');
    await confirmationContext.close();
  }

  const groomingContext = await browser.newContext();
  const groomingPage = await groomingContext.newPage();
  await loginStaff(groomingPage, process.env.AV_DEMO_GROOMER_AURORA_EMAIL, process.env.AV_DEMO_GROOMER_AURORA_PASSWORD);
  await groomingPage.goto(`${baseUrl}/grooming`, { waitUntil: 'networkidle' });
  await groomingPage.getByRole('heading', { name: 'Mis servicios de grooming' }).waitFor();
  const reloaded = groomingPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/grooming/tasks');
  await groomingPage.getByRole('button', { name: 'Actualizar tareas' }).click();
  assert.equal((await reloaded).status(), 200, 'the assigned groomer must reload its server-authorized task list');
  const task = (await browserFetch(groomingPage, '/api/v1/grooming/tasks')).body?.data
    ?.find((item) => item.requestId === request.id);
  assert.ok(task?.reservationId, 'only the assigned groomer must receive the operational task');
  assert.equal(await groomingPage.getByText(/historia clínica/i).count(), 0, 'the grooming view must not expose clinical history');
  const taskRow = groomingPage.locator(`[data-reservation-id="${task.reservationId}"]`);
  if (task.reservationStatus === 'CONFIRMADA') {
    const startButton = taskRow.getByRole('button', { name: 'Iniciar servicio' });
    await startButton.waitFor();
    assert.equal(await startButton.isDisabled(), false, 'the assigned groomer must be able to start its confirmed task');
    const started = groomingPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/grooming/reservations/${task.reservationId}/start`));
    await startButton.click();
    assert.equal((await started).status(), 200, 'the assigned groomer must start the operational task through the visible action');
  }

  const refreshedTask = (await browserFetch(groomingPage, '/api/v1/grooming/tasks')).body?.data
    ?.find((item) => item.requestId === request.id);
  if (refreshedTask?.reservationStatus === 'EN_ATENCION') {
    await groomingPage.getByRole('button', { name: 'Completar servicio' }).waitFor();
    await groomingPage.getByRole('button', { name: 'Completar servicio' }).click();
    await groomingPage.getByLabel('Resultado del servicio').fill('Baño y cepillado completados.');
    const completed = groomingPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/grooming/reservations/${task.reservationId}/complete`));
    await groomingPage.getByRole('button', { name: 'Confirmar finalización' }).click();
    assert.equal((await completed).status(), 200, 'the assigned groomer must complete the task through the visible form');
  }

  const finalTask = (await browserFetch(groomingPage, '/api/v1/grooming/tasks')).body?.data
    ?.find((item) => item.requestId === request.id);
  assert.equal(finalTask?.reservationStatus, 'COMPLETADA', 'the grooming task must reach its terminal operational status exactly once');
  assert.ok(finalTask?.attention?.completedAt, 'the completed task must retain an operational completion timestamp');
  if (screenshotPath) {
    await mkdir(dirname(screenshotPath), { recursive: true });
    await groomingPage.screenshot({ path: screenshotPath, fullPage: true });
  }
  await groomingContext.close();

  const finalContext = await browser.newContext();
  const finalPage = await finalContext.newPage();
  await loginStaff(finalPage, administratorEmail, process.env.AV_DEMO_AURORA_PASSWORD);
  const finalRequest = (await browserFetch(finalPage, '/api/v1/schedule/requests')).body?.data
    ?.find((item) => item.id === request.id);
  assert.equal(finalRequest?.status, 'COMPLETADA', 'administration must observe the completed status without a second reservation');
  await finalContext.close();
  process.stdout.write('B03_GROOMING_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page, email, password) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(email);
  await page.getByLabel(/Contrase/).fill(password);
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
