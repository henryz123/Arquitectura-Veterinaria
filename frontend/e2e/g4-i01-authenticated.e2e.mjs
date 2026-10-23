import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_E2E_BASE_URL',
  'AV_E2E_AURORA_EMAIL',
  'AV_DEMO_AURORA_PASSWORD',
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
  const page = await browser.newPage();
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
  await page.getByLabel('Correo institucional').fill(process.env.AV_E2E_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await page.waitForURL(`${baseUrl}/workspace`);
  await assert.doesNotReject(() => page.getByRole('heading', { name: 'Espacio de trabajo' }).waitFor());
  const tenantName = page.locator('.workspace-content article h2');
  await assert.doesNotReject(() => tenantName.waitFor());
  assert.equal(tenantProfileStatus, 200, 'the protected tenant profile must load successfully');
  assert.ok((await tenantName.textContent())?.trim(), 'the protected tenant profile must contain a commercial name');

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  assert.ok(logoutCsrfHeader, 'logout must include the in-memory CSRF token');

  await page.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  process.stdout.write('G4_I01_AUTHENTICATED_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}
