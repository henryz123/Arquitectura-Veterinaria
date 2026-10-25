import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_BOSQUE_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const serviceFixture = {
  serviceType: 'CLINICA',
  name: 'B03 Consulta E2E',
  durationMinutes: 30,
  referencePrice: 45,
};
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const context = await browser.newContext();
  const page = await context.newPage();
  await login(page, 'veterinaria-aurora', process.env.AV_DEMO_AURORA_PASSWORD);

  const scheduleLoad = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/schedule/services');
  await page.getByRole('link', { name: 'Agenda' }).click();
  await page.waitForURL(`${baseUrl}/schedule`);
  await page.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();
  assert.equal((await scheduleLoad).status(), 200, 'the schedule page must read tenant-local services');

  let services = await browserFetch(page, '/api/v1/schedule/services');
  assert.equal(services.status, 200, 'an authorized staff session must list its own service catalog');
  let service = services.body?.data?.find((item) => item.name === serviceFixture.name) ?? null;
  if (!service) {
    const createService = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/services');
    await page.locator('#service-type').selectOption(serviceFixture.serviceType);
    await page.locator('#service-name').fill(serviceFixture.name);
    await page.locator('#service-duration').fill(String(serviceFixture.durationMinutes));
    await page.locator('#service-price').fill(String(serviceFixture.referencePrice));
    await page.getByRole('button', { name: 'Guardar servicio' }).click();
    const response = await createService;
    assert.equal(response.status(), 201, 'the service form must persist through the protected API');
    service = (await response.json()).data;
  }
  assert.equal(service.serviceType, 'CLINICA', 'the persisted service must retain its functional type');
  assert.equal(service.durationMinutes, 30, 'the persisted service must retain a 15-minute-compatible duration');
  assert.equal(service.referencePrice, 45, 'the persisted service must retain its reference price');

  let operatingHours = await browserFetch(page, '/api/v1/schedule/operating-hours');
  assert.equal(operatingHours.status, 200, 'the operating-hours endpoint must remain tenant protected');
  if (!operatingHours.body?.data?.some((item) => item.weekday === 1 && item.startsAtLocal === '08:00:00'
      && item.endsAtLocal === '18:00:00')) {
    const createOperatingHours = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/operating-hours');
    await page.locator('#operating-weekday').selectOption('1');
    await page.locator('#operating-start').fill('08:00');
    await page.locator('#operating-end').fill('18:00');
    await page.getByRole('button', { name: 'Guardar horario' }).click();
    assert.equal((await createOperatingHours).status(), 201, 'the operating schedule must persist through the UI');
  }

  const professionals = await browserFetch(page, '/api/v1/schedule/assignable-professionals');
  assert.equal(professionals.status, 200, 'the UI must retrieve a server-authorized professional projection');
  const professional = professionals.body?.data?.[0];
  assert.ok(professional?.principalId, 'the B03 fixture requires an active assignable professional');
  const availability = await browserFetch(page, '/api/v1/schedule/professional-availability');
  assert.equal(availability.status, 200, 'the availability endpoint must remain tenant protected');
  if (!availability.body?.data?.some((item) => item.professionalPrincipalId === professional.principalId && item.weekday === 1
      && item.startsAtLocal === '08:00:00' && item.endsAtLocal === '12:00:00')) {
    const createAvailability = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/schedule/professional-availability');
    await page.locator('#assignable-professional').selectOption(professional.principalId);
    await page.locator('#professional-weekday').selectOption('1');
    await page.locator('#professional-start').fill('08:00');
    await page.locator('#professional-end').fill('12:00');
    await page.getByRole('button', { name: 'Guardar disponibilidad' }).click();
    assert.equal((await createAvailability).status(), 201, 'availability must persist only after server-side role revalidation');
  }

  const storedAvailability = await browserFetch(page, '/api/v1/schedule/professional-availability');
  assert.ok(storedAvailability.body?.data?.some((item) => item.professionalPrincipalId === professional.principalId
    && item.weekday === 1 && item.startsAtLocal === '08:00:00' && item.endsAtLocal === '12:00:00'),
  'the schedule page must display availability persisted in PostgreSQL');

  const bosqueContext = await browser.newContext();
  const bosquePage = await bosqueContext.newPage();
  await login(bosquePage, 'veterinaria-bosque', process.env.AV_DEMO_BOSQUE_PASSWORD);
  const bosqueServices = await browserFetch(bosquePage, '/api/v1/schedule/services');
  assert.equal(bosqueServices.status, 200, 'Bosque administration must read only its own schedule catalog');
  assert.equal((bosqueServices.body?.data ?? []).some((item) => item.id === service.id), false,
    'a schedule service created in Aurora must not be visible from Bosque');
  await bosqueContext.close();

  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false,
    'the schedule page must not overflow horizontally on a narrow viewport');
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  await context.close();
  process.stdout.write('B03_SERVICES_AVAILABILITY_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function login(page, tenantSlug, password) {
  await page.goto(`${baseUrl}/login/${tenantSlug}`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
}

async function browserFetch(page, path) {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, path);
}
