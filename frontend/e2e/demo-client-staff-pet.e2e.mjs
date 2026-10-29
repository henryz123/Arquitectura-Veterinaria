import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
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
const screenshotPath = process.env.AV_DEMO_SCREENSHOT_PATH
  ? resolve(process.env.AV_DEMO_SCREENSHOT_PATH)
  : null;
const petFixture = {
  name: 'Luna',
  species: 'Canino',
  breed: 'Mestizo',
  sex: 'HEMBRA',
  estimatedAgeMonths: 24,
  distinguishingMarks: 'Ficha sintética para la demostración académica.',
};
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await loginClient(clientPage);
  await clientPage.getByRole('button', { name: 'Mis mascotas' }).click();
  await clientPage.getByRole('heading', { name: 'Mis mascotas' }).waitFor();

  const ownerResponse = await browserFetch(clientPage, '/api/v1/client/owner-profile');
  assert.equal(ownerResponse.status, 200, 'the approved CLIENT must retain a tenant-local owner profile');
  const owner = ownerResponse.body?.data;
  assert.ok(owner?.id && owner.givenNames && owner.familyNames, 'the CLIENT profile must identify its own authorized owner');

  let pet = await findPet(clientPage, petFixture.name);
  if (!pet) {
    await clientPage.getByRole('button', { name: 'Registrar mascota' }).click();
    await clientPage.getByRole('heading', { name: 'Registrar mascota' }).waitFor();
    await clientPage.locator('input[name="newPetName"]').fill(petFixture.name);
    await clientPage.locator('input[name="newPetSpecies"]').fill(petFixture.species);
    await clientPage.locator('input[name="newPetBreed"]').fill(petFixture.breed);
    await clientPage.locator('select[name="newPetSex"]').selectOption(petFixture.sex);
    await clientPage.locator('input[name="newPetEstimatedAge"]').fill(String(petFixture.estimatedAgeMonths));
    await clientPage.locator('input[name="newPetMarks"]').fill(petFixture.distinguishingMarks);
    const created = clientPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/pets');
    await clientPage.locator('#client-register-pet').getByRole('button', { name: 'Registrar mascota' }).click();
    assert.equal((await created).status(), 201, 'the visible CLIENT form must persist Luna');
    pet = await findPet(clientPage, petFixture.name);
  }
  assert.ok(pet?.id, 'Luna must be visible in the CLIENT list after registration');

  const petCard = clientPage.locator('.pet-cards article').filter({ hasText: petFixture.name }).first();
  await petCard.getByRole('button', { name: 'Editar ficha basica' }).click();
  await clientPage.locator('input[name="editPetMarks"]').fill(petFixture.distinguishingMarks);
  const updated = clientPage.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/v1/client/pets/${pet.id}`);
  await clientPage.getByRole('button', { name: 'Guardar ficha' }).click();
  assert.equal((await updated).status(), 200, 'the CLIENT must update only Luna basic data through the visible form');

  await clientPage.reload({ waitUntil: 'networkidle' });
  const persisted = await browserFetch(clientPage, `/api/v1/client/pets/${pet.id}`);
  assert.equal(persisted.status, 200, 'Luna must remain available after an authenticated browser reload');
  assert.equal(persisted.body?.data?.name, petFixture.name, 'the persisted record must retain the demonstration name');
  assert.equal(persisted.body?.data?.distinguishingMarks, petFixture.distinguishingMarks,
    'the visible CLIENT edit must persist its permitted basic field');
  if (screenshotPath) {
    await mkdir(dirname(screenshotPath), { recursive: true });
    await clientPage.screenshot({ path: screenshotPath, fullPage: true });
  }
  await clientContext.close();

  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage);
  await staffPage.goto(`${baseUrl}/care`, { waitUntil: 'networkidle' });
  await staffPage.getByRole('heading', { name: 'Clientes y mascotas' }).waitFor();

  const ownerPets = await browserFetch(staffPage, `/api/v1/owners/${owner.id}/pets`);
  assert.equal(ownerPets.status, 200, 'the authorized STAFF account must read tenant-local pets of the linked owner');
  assert.equal(ownerPets.body?.data?.some((item) => item.id === pet.id && item.name === petFixture.name), true,
    'the STAFF API projection must expose the same persisted Luna record');

  const ownerLabel = `${owner.familyNames}, ${owner.givenNames}`;
  const listedPets = staffPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/owners/${owner.id}/pets`);
  await staffPage.locator('.owner-list button').filter({ hasText: ownerLabel }).first().click();
  assert.equal((await listedPets).status(), 200, 'selecting the owner in the STAFF screen must load its real pets');
  await staffPage.locator('.pet-list button').filter({ hasText: petFixture.name }).waitFor();
  await staffContext.close();

  process.stdout.write('DEMO_CLIENT_STAFF_PET_BROWSER_E2E=PASS\n');
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

async function findPet(page, name) {
  const response = await browserFetch(page, '/api/v1/client/pets');
  assert.equal(response.status, 200, 'the CLIENT must read only its own authorized pets');
  return response.body?.data?.find((pet) => pet.name === name) ?? null;
}

async function browserFetch(page, path) {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, path);
}
