import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_BOSQUE_PASSWORD',
  'AV_DEMO_VETERINARIO_AURORA_EMAIL',
  'AV_DEMO_VETERINARIO_AURORA_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const auroraEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const aurora = await browser.newContext();
  const auroraPage = await aurora.newPage();
  await login(auroraPage, 'veterinaria-aurora', auroraEmail, process.env.AV_DEMO_AURORA_PASSWORD);

  const loadOptions = auroraPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/schedule/assignable-professionals');
  await auroraPage.getByRole('link', { name: 'Agenda' }).click();
  await auroraPage.waitForURL(`${baseUrl}/schedule`);
  await auroraPage.getByRole('heading', { name: 'Profesionales asignables' }).waitFor();
  const auroraOptions = await loadOptions;
  assert.equal(auroraOptions.status(), 200, 'Aurora administration must retrieve its assignable professionals');
  const auroraPayload = await auroraOptions.json();
  assert.ok(Array.isArray(auroraPayload.data), 'the endpoint must keep the documented envelope');
  for (const professional of auroraPayload.data) {
    assert.deepEqual(Object.keys(professional).sort(), ['principalId', 'roles'], 'the projection must not expose extra identity data');
    assert.ok(professional.roles.length > 0 && professional.roles.every((role) => ['VETERINARIO', 'GROOMING'].includes(role)),
      'only active clinical or grooming roles can be returned');
  }
  const auroraProfessionalIds = new Set(auroraPayload.data.map((professional) => professional.principalId));
  await auroraPage.locator('#assignable-professional').waitFor();
  await aurora.close();

  const bosque = await browser.newContext();
  const bosquePage = await bosque.newPage();
  await login(bosquePage, 'veterinaria-bosque', auroraEmail, process.env.AV_DEMO_BOSQUE_PASSWORD);
  const bosqueOptions = await browserFetch(bosquePage, '/api/v1/schedule/assignable-professionals');
  assert.equal(bosqueOptions.status, 200, 'Bosque administration must receive its own tenant projection');
  for (const professional of bosqueOptions.body?.data ?? []) {
    assert.equal(auroraProfessionalIds.has(professional.principalId), false,
      'Bosque must not receive a professional identifier returned to Aurora');
  }
  await bosque.close();

  const veterinarian = await browser.newContext();
  const veterinarianPage = await veterinarian.newPage();
  await login(veterinarianPage, 'veterinaria-aurora', process.env.AV_DEMO_VETERINARIO_AURORA_EMAIL,
    process.env.AV_DEMO_VETERINARIO_AURORA_PASSWORD);
  const denied = await browserFetch(veterinarianPage, '/api/v1/schedule/assignable-professionals');
  assert.equal(denied.status, 403, 'a veterinarian without administration or reception role must be denied by the server');
  await veterinarian.close();

  process.stdout.write('B03_ASSIGNABLE_PROFESSIONALS_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function login(page, tenantSlug, email, password) {
  await page.goto(`${baseUrl}/login/${tenantSlug}`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(email);
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
