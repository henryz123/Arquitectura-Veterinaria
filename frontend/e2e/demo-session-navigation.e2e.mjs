import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_CLIENT_AURORA_EMAIL',
  'AV_DEMO_CLIENT_AURORA_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local demo environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await loginClient(clientPage);

  const clientSession = await browserFetch(clientPage, '/api/v1/session-context');
  assert.equal(clientSession.status, 200, 'the browser must recover the authenticated CLIENT context');
  assert.equal(clientSession.body?.data?.accountType, 'CLIENT', 'the client login must establish a CLIENT session');

  await clientPage.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await clientPage.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  await clientPage.getByRole('heading', { name: 'Acceso al espacio de trabajo' }).waitFor();
  await clientContext.close();

  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage);

  const staffSession = await browserFetch(staffPage, '/api/v1/session-context');
  assert.equal(staffSession.status, 200, 'the browser must return the STAFF session context');
  assert.equal(staffSession.body?.data?.accountType, 'STAFF', 'the staff login must establish a STAFF session');
  assert.equal(staffSession.body?.data?.roles?.includes('ADMIN_VETERINARIA'), true,
    'the synthetic Aurora administrator must retain its administrative role');
  const ownTenant = await browserFetch(staffPage, '/api/v1/tenant-profile/veterinaria-aurora');
  assert.equal(ownTenant.status, 200, 'the STAFF session must read only its own tenant profile');
  const staffAccounts = await browserFetch(staffPage, '/api/v1/staff-accounts');
  assert.equal(staffAccounts.status, 200, 'the administrator must read the tenant-local staff list');
  await staffPage.getByRole('link', { name: 'Inventario y catálogo' }).waitFor();
  await staffPage.getByRole('heading', { name: 'Información de la veterinaria' }).waitFor();
  await staffPage.getByRole('link', { name: 'Organización' }).click();
  await staffPage.waitForURL(`${baseUrl}/workspace#organization`);
  await staffPage.locator('#organization').waitFor();
  assert.equal(await staffPage.locator('text=Opciones disponibles para tu cuenta').count(), 0,
    'the administrator must not render the restricted workspace state');

  await staffPage.reload({ waitUntil: 'networkidle' });
  await staffPage.waitForURL(`${baseUrl}/workspace#organization`);
  await staffPage.getByRole('link', { name: 'Inventario y catálogo' }).waitFor();
  await staffContext.close();

  process.stdout.write('DEMO_SESSION_NAVIGATION_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginClient(page) {
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
}

async function loginStaff(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_AURORA_PASSWORD);
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
