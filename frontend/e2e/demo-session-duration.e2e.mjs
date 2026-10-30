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
const inactivityMs = Number(process.env.AV_E2E_SESSION_INACTIVITY_MS ?? 720_000);
if (!Number.isInteger(inactivityMs) || inactivityMs < 720_000 || inactivityMs > 900_000) {
  throw new Error('AV_E2E_SESSION_INACTIVITY_MS must stay between 12 and 15 real minutes.');
}
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const clientContext = await browser.newContext();
  const staffContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  const staffPage = await staffContext.newPage();

  await Promise.all([loginClient(clientPage), loginStaff(staffPage)]);
  await Promise.all([
    clientPage.reload({ waitUntil: 'networkidle' }),
    staffPage.reload({ waitUntil: 'networkidle' }),
  ]);

  await Promise.all([clientPage.waitForTimeout(inactivityMs), staffPage.waitForTimeout(inactivityMs)]);

  const [clientSession, staffSession] = await Promise.all([
    browserFetch(clientPage, '/api/v1/session-context'),
    browserFetch(staffPage, '/api/v1/session-context'),
  ]);
  assert.equal(clientSession.status, 200, 'CLIENT must remain authenticated after more than two minutes of ordinary activity');
  assert.equal(clientSession.body?.data?.accountType, 'CLIENT', 'CLIENT context must survive browser reload and inactivity wait');
  assert.equal(staffSession.status, 200, 'STAFF must remain authenticated after more than two minutes of ordinary activity');
  assert.equal(staffSession.body?.data?.accountType, 'STAFF', 'STAFF context must survive browser reload and inactivity wait');

  const [clientPets, staffInventory] = await Promise.all([
    browserFetch(clientPage, '/api/v1/client/pets'),
    browserFetch(staffPage, '/api/v1/inventory/stock'),
  ]);
  assert.equal(clientPets.status, 200, 'CLIENT must complete an authorized read after the wait');
  assert.equal(staffInventory.status, 200, 'STAFF must complete an authorized read after the wait');

  await clientPage.getByRole('button', { name: 'Mis datos' }).click();
  const clientProfile = clientPage.locator('#client-profile form');
  await clientProfile.getByRole('button', { name: 'Guardar datos basicos' }).click();
  await clientPage.getByText(/Tus datos basicos fueron actualizados/).waitFor();
  await staffPage.getByRole('link', { name: 'Citas y agenda' }).click();
  await staffPage.waitForURL(`${baseUrl}/schedule`);
  await staffPage.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();

  await Promise.all([
    clientPage.reload({ waitUntil: 'networkidle' }),
    staffPage.reload({ waitUntil: 'networkidle' }),
  ]);
  const [clientAfterReload, staffAfterReload] = await Promise.all([
    browserFetch(clientPage, '/api/v1/session-context'),
    browserFetch(staffPage, '/api/v1/session-context'),
  ]);
  assert.equal(clientAfterReload.status, 200, 'CLIENT must remain authenticated after its authorized write and reload');
  assert.equal(staffAfterReload.status, 200, 'STAFF must remain authenticated after navigation and reload');

  await clientPage.getByRole('button', { name: /Cerrar sesi/i }).click();
  await clientPage.waitForURL(`${baseUrl}/client/login/veterinaria-aurora`);
  await staffPage.getByRole('button', { name: /Cerrar sesi/i }).click();
  await staffPage.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  const [clientAfterLogout, staffAfterLogout] = await Promise.all([
    browserFetch(clientPage, '/api/v1/session-context'),
    browserFetch(staffPage, '/api/v1/session-context'),
  ]);
  assert.equal(clientAfterLogout.status, 401, 'CLIENT logout must revoke the opaque session');
  assert.equal(staffAfterLogout.status, 401, 'STAFF logout must revoke the opaque session');

  await Promise.all([
    clientPage.goto(`${baseUrl}/client-space`, { waitUntil: 'networkidle' }),
    staffPage.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' }),
  ]);
  await clientPage.getByText('Tu sesión venció o dejó de ser válida. Inicia sesión nuevamente para continuar.').waitFor();
  await staffPage.getByText('Tu sesión venció o dejó de ser válida. Inicia sesión nuevamente para continuar.').waitFor();

  await Promise.all([clientContext.close(), staffContext.close()]);
  process.stdout.write(`DEMO_SESSION_DURATION_BROWSER_E2E=PASS inactivityMs=${inactivityMs}\n`);
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
