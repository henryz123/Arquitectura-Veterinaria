import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { chromium } from 'playwright-core';

const required = [
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
const petName = `Mascota demo ${Date.now()}`;
const screenshotPath = process.env.AV_DEMO_SCREENSHOT_PATH
  ? resolve(process.env.AV_DEMO_SCREENSHOT_PATH)
  : null;
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
  await page.getByRole('button', { name: 'Registrar mascota' }).click();
  await page.getByRole('heading', { name: 'Registrar mascota' }).waitFor();

  await page.locator('input[name="newPetName"]').fill(petName);
  await page.locator('input[name="newPetSpecies"]').fill('Canino');
  await page.locator('#client-register-pet').getByRole('button', { name: 'Registrar mascota' }).click();
  await page.getByRole('alert').filter({ hasText: 'Indica una fecha de nacimiento o una edad estimada' }).waitFor();

  await page.locator('input[name="newPetBreed"]').fill('Mestizo');
  await page.locator('select[name="newPetSex"]').selectOption('HEMBRA');
  await page.locator('input[name="newPetEstimatedAge"]').fill('24');
  const created = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/client/pets');
  await page.locator('#client-register-pet').getByRole('button', { name: 'Registrar mascota' }).click();
  const createResponse = await created;
  assert.equal(createResponse.status(), 201, 'the client pet form must persist the new authorized pet');
  const pet = (await createResponse.json()).data;
  assert.ok(pet?.id, 'the create response must identify the persisted pet');
  await page.locator('.pet-cards article').filter({ hasText: petName }).waitFor();
  await page.getByText('La ficha basica de tu mascota fue registrada. La historia clinica sigue siendo privada.').waitFor();

  const reloadedPets = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/client/pets');
  await page.reload({ waitUntil: 'networkidle' });
  assert.equal((await reloadedPets).status(), 200, 'the client pet list must reload from the authenticated API');
  await page.getByRole('button', { name: 'Mis mascotas', exact: true }).click();
  await page.locator('.pet-cards article').filter({ hasText: petName }).waitFor();
  const persisted = await browserFetch(page, `/api/v1/client/pets/${pet.id}`);
  assert.equal(persisted.status, 200, 'the persisted pet must be readable after a full page reload');
  assert.equal(persisted.body?.data?.name, petName, 'the persisted pet must retain the submitted synthetic name');

  if (screenshotPath) {
    await mkdir(dirname(screenshotPath), { recursive: true });
    await page.screenshot({ path: screenshotPath, fullPage: true });
  }
  await context.close();
  process.stdout.write('CLIENT_PET_DEMO_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function browserFetch(page, path) {
  return page.evaluate(async (resourcePath) => {
    const response = await fetch(resourcePath);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, path);
}
