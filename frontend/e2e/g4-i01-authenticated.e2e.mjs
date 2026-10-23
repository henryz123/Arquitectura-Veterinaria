import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_E2E_BASE_URL',
  'AV_E2E_AURORA_EMAIL',
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_BOSQUE_PASSWORD',
  'AV_DEMO_SAAS_EMAIL',
  'AV_DEMO_SAAS_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = process.env.AV_E2E_BASE_URL.replace(/\/$/, '');
const browser = await chromium.launch({
  executablePath: process.env.AV_E2E_CHROME_PATH,
  headless: true,
});

try {
  const auroraContext = await browser.newContext();
  const page = await auroraContext.newPage();
  let logoutCsrfHeader;
  let tenantProfileStatus;
  page.on('request', (request) => {
    if (request.method() === 'DELETE' && request.url().endsWith('/api/v1/auth/session')) {
      logoutCsrfHeader = request.headers()['x-csrf-token'];
    }
  });
  page.on('response', (response) => {
    if (response.url().includes('/api/v1/tenant-profile/')) {
      tenantProfileStatus = response.status();
    }
  });

  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await assert.doesNotReject(() => page.getByRole('main').waitFor());
  assert.equal(await page.locator('#email').evaluate((input) => input.labels?.length), 1, 'email must have a native label');
  assert.equal(await page.locator('#password').evaluate((input) => input.labels?.length), 1, 'password must have a native label');
  assert.equal(await page.locator('#email').getAttribute('aria-describedby'), null, 'a valid field must not reference an absent error');
  await page.locator('#email').focus();
  assert.equal(await page.evaluate(() => document.activeElement?.id), 'email', 'keyboard focus must reach email first');
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement?.id), 'password', 'keyboard focus must proceed to password');
  await page.getByLabel('Correo institucional').fill(process.env.AV_E2E_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await page.waitForURL(`${baseUrl}/workspace`);
  await assert.doesNotReject(() => page.getByRole('heading', { name: 'Espacio de trabajo' }).waitFor());
  const tenantName = page.locator('.workspace-content article h2');
  await assert.doesNotReject(() => tenantName.waitFor());
  assert.equal(tenantProfileStatus, 200, 'the protected tenant profile must load successfully');
  assert.ok((await tenantName.textContent())?.trim(), 'the protected tenant profile must contain a commercial name');
  const auroraName = (await tenantName.textContent())?.trim();

  const staffAccounts = await browserFetch(page, '/api/v1/staff-accounts');
  assert.equal(staffAccounts.status, 200, 'an authenticated STAFF session must access its B01 staff list');
  assert.ok(Array.isArray(staffAccounts.body?.data), 'the B01 staff list must preserve its envelope');
  const staffClientSpace = await browserFetch(page, '/api/v1/client-space');
  assert.equal(staffClientSpace.status, 403, 'a STAFF session must not access CLIENT space');

  const bosqueContext = await browser.newContext();
  const bosquePage = await bosqueContext.newPage();
  await loginStaff(bosquePage, 'veterinaria-bosque', process.env.AV_DEMO_BOSQUE_PASSWORD);
  const bosqueName = (await bosquePage.locator('.workspace-content article h2').textContent())?.trim();
  assert.ok(bosqueName, 'Bosque must load its own protected profile');
  assert.notEqual(bosqueName, auroraName, 'Aurora and Bosque must retain independent tenant context');
  const crossTenantProfile = await browserFetch(bosquePage, '/api/v1/tenant-profile/veterinaria-aurora');
  assert.equal(crossTenantProfile.status, 404, 'Bosque must not learn whether Aurora profile is available');
  await logoutStaff(bosquePage);
  await bosqueContext.close();

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  assert.ok(logoutCsrfHeader, 'logout must include the in-memory CSRF token');

  await page.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);

  const saasContext = await browser.newContext();
  const saasPage = await saasContext.newPage();
  await saasPage.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
  const saasLogin = await browserFetch(saasPage, '/api/v1/auth/saas-sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: process.env.AV_DEMO_SAAS_EMAIL, password: process.env.AV_DEMO_SAAS_PASSWORD }),
  });
  assert.equal(saasLogin.status, 201, 'SaaS authentication must create an opaque session');
  const saasCsrf = saasLogin.body?.data?.csrfToken;
  assert.equal(typeof saasCsrf, 'string', 'SaaS login must return the in-memory CSRF token');
  const organizations = await browserFetch(saasPage, '/api/v1/tenants');
  assert.equal(organizations.status, 200, 'a SaaS session must access B01 organization administration');
  const missingCsrf = await browserFetch(saasPage, '/api/v1/auth/session', { method: 'DELETE' });
  assert.equal(missingCsrf.status, 403, 'SaaS logout must reject a missing CSRF token');
  const saasLogout = await browserFetch(saasPage, '/api/v1/auth/session', {
    method: 'DELETE',
    headers: { 'X-CSRF-Token': saasCsrf },
  });
  assert.equal(saasLogout.status, 204, 'SaaS logout with its CSRF token must succeed');
  const revokedSaasSession = await browserFetch(saasPage, '/api/v1/session-context');
  assert.equal(revokedSaasSession.status, 401, 'SaaS logout must revoke the session');
  await saasContext.close();
  await auroraContext.close();
  process.stdout.write('G4_I01_AUTHENTICATED_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page, tenantSlug, password) {
  await page.goto(`${baseUrl}/login/${tenantSlug}`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(process.env.AV_E2E_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
  await page.getByRole('heading', { name: 'Espacio de trabajo' }).waitFor();
}

async function logoutStaff(page) {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(/\/login\/veterinaria-/);
}

async function browserFetch(page, path, options = {}) {
  return page.evaluate(async ({ path, options }) => {
    const response = await fetch(path, options);
    const text = await response.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }
    return { status: response.status, body };
  }, { path, options });
}
