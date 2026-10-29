import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_CLIENT_AURORA_EMAIL',
  'AV_DEMO_CLIENT_AURORA_PASSWORD',
  'AV_DEMO_VETERINARIO_AURORA_EMAIL',
  'AV_DEMO_VETERINARIO_AURORA_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local demo environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const clinicalFixture = {
  petName: 'Luna',
  reason: 'Control veterinario preventivo',
  anamnesis: 'Revisión general programada.',
  assessment: 'Mascota estable, sin hallazgos relevantes.',
  diagnosis: 'Evaluación preventiva de rutina.',
  treatmentPlan: 'Seguimiento según evolución.',
};
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await loginClient(clientPage);
  const ownerResponse = await browserFetch(clientPage, '/api/v1/client/owner-profile');
  const lunaResponse = await browserFetch(clientPage, '/api/v1/client/pets');
  assert.equal(ownerResponse.status, 200, 'the CLIENT owner profile must be available before the veterinarian demonstration');
  assert.equal(lunaResponse.status, 200, 'the CLIENT pet list must be available before the veterinarian demonstration');
  const owner = ownerResponse.body?.data;
  const luna = lunaResponse.body?.data?.find((pet) => pet.name === clinicalFixture.petName);
  assert.ok(owner?.id && owner.givenNames && owner.familyNames, 'the demonstration owner must be linked to the CLIENT account');
  assert.ok(luna?.id, 'Luna must exist before a veterinarian records an attention');
  await clientContext.close();

  const veterinarianContext = await browser.newContext();
  const veterinarianPage = await veterinarianContext.newPage();
  await loginVeterinarian(veterinarianPage);
  const session = await browserFetch(veterinarianPage, '/api/v1/session-context');
  assert.equal(session.body?.data?.accountType, 'STAFF', 'the veterinarian session must be a STAFF session');
  assert.equal(session.body?.data?.roles?.includes('VETERINARIO'), true, 'the local synthetic account must retain VETERINARIO');

  await veterinarianPage.goto(`${baseUrl}/care`, { waitUntil: 'networkidle' });
  await veterinarianPage.getByRole('heading', { name: 'Clientes y mascotas' }).waitFor();
  const ownerLabel = `${owner.familyNames}, ${owner.givenNames}`;
  const loadedPets = veterinarianPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/owners/${owner.id}/pets`);
  await veterinarianPage.locator('.owner-list button').filter({ hasText: ownerLabel }).first().click();
  assert.equal((await loadedPets).status(), 200, 'the veterinarian UI must load the linked owner pets');
  const loadedHistory = veterinarianPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/pets/${luna.id}/clinical-episodes`);
  await veterinarianPage.locator('.pet-list button').filter({ hasText: clinicalFixture.petName }).first().click();
  assert.equal((await loadedHistory).status(), 200, 'the veterinarian UI must load Luna clinical history');

  const existingHistory = await browserFetch(veterinarianPage, `/api/v1/pets/${luna.id}/clinical-episodes`);
  assert.equal(existingHistory.status, 200, 'the veterinarian may read the protected history for the selected pet');
  if (!existingHistory.body?.data?.some((episode) => episode.reason === clinicalFixture.reason)) {
    await veterinarianPage.locator('#clinical-reason').fill(clinicalFixture.reason);
    await veterinarianPage.locator('#clinical-anamnesis').fill(clinicalFixture.anamnesis);
    await veterinarianPage.locator('#clinical-assessment').fill(clinicalFixture.assessment);
    await veterinarianPage.locator('#clinical-diagnosis').fill(clinicalFixture.diagnosis);
    await veterinarianPage.locator('#clinical-plan').fill(clinicalFixture.treatmentPlan);
    const finalized = veterinarianPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.includes('/clinical-episodes/')
      && new URL(response.url()).pathname.endsWith('/finalization'));
    await veterinarianPage.getByRole('button', { name: 'Registrar y finalizar atención' }).click();
    assert.equal((await finalized).status(), 200, 'the visible veterinarian form must finalize the preventive attention');
  }

  await veterinarianPage.reload({ waitUntil: 'networkidle' });
  const reloadedPets = veterinarianPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/owners/${owner.id}/pets`);
  await veterinarianPage.locator('.owner-list button').filter({ hasText: ownerLabel }).first().click();
  await reloadedPets;
  const reloadedHistory = veterinarianPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/pets/${luna.id}/clinical-episodes`);
  await veterinarianPage.locator('.pet-list button').filter({ hasText: clinicalFixture.petName }).first().click();
  assert.equal((await reloadedHistory).status(), 200, 'the reloaded veterinarian session must retrieve history from PostgreSQL');
  await veterinarianPage.locator('.clinical-list').getByText(clinicalFixture.reason, { exact: true }).waitFor();
  await veterinarianContext.close();

  process.stdout.write('DEMO_VETERINARIAN_CARE_BROWSER_E2E=PASS\n');
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

async function loginVeterinarian(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(process.env.AV_DEMO_VETERINARIO_AURORA_EMAIL);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_VETERINARIO_AURORA_PASSWORD);
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
