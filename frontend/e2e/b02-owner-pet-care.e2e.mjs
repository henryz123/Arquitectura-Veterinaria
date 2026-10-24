import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const ownerFixture = {
  givenNames: 'B02 E2E',
  familyNames: 'Aurora',
  phone: '900000000',
  email: null,
  address: 'Registro sintético local',
};
const successorFixture = {
  givenNames: 'B02 Transferencia',
  familyNames: 'Aurora',
  phone: '900000001',
  address: 'Synthetic transfer record',
};
const petFixture = {
  name: 'Nube E2E',
  species: 'Canino',
  breed: 'Mestizo',
  sex: 'HEMBRA',
  estimatedAgeMonths: 24,
};
const evidenceDirectory = process.env.AV_E2E_EVIDENCE_DIR
  ? resolve(process.env.AV_E2E_EVIDENCE_DIR)
  : null;
if (evidenceDirectory) {
  await mkdir(evidenceDirectory, { recursive: true });
}

const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const context = await browser.newContext();
  const page = await context.newPage();
  await login(page);

  const initialOwnersLoad = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/owners');
  await page.getByRole('link', { name: 'Propietarios y clínica' }).click();
  await page.waitForURL(`${baseUrl}/care`);
  await page.getByRole('heading', { name: 'Registrar y atender' }).waitFor();
  assert.equal((await initialOwnersLoad).status(), 200, 'the B02 page must load the tenant owner list');

  let owner = await existingOwner(page);
  if (!owner) {
    const [response] = await Promise.all([
      page.waitForResponse((candidate) => candidate.request().method() === 'POST'
        && candidate.url().endsWith('/api/v1/owners')),
      (async () => {
        await page.getByLabel('Nombres', { exact: true }).fill(ownerFixture.givenNames);
        await page.getByLabel('Apellidos', { exact: true }).fill(ownerFixture.familyNames);
        await page.getByLabel('Teléfono', { exact: true }).fill(ownerFixture.phone);
        await page.getByLabel('Dirección', { exact: true }).fill(ownerFixture.address);
        await page.getByRole('button', { name: 'Registrar propietario' }).click();
      })(),
    ]);
    assert.equal(response.status(), 201, 'the owner form must persist through the authenticated API');
    owner = (await response.json()).data;
  }

  const ownerButton = page.locator('.owner-list button').filter({ hasText: `${ownerFixture.familyNames}, ${ownerFixture.givenNames}` });
  await ownerButton.waitFor();
  await ownerButton.click();

  const successor = await ensureSuccessor(page);
  const originalOwnerButton = page.locator('.owner-list button').filter({ hasText: `${ownerFixture.familyNames}, ${ownerFixture.givenNames}` });
  await originalOwnerButton.waitFor();
  await originalOwnerButton.click();
  let pet = await existingPet(page, owner.id);
  if (!pet) {
    const transferredPet = await existingPet(page, successor.id);
    if (transferredPet) {
      pet = transferredPet;
      owner = successor;
      const successorButton = page.locator('.owner-list button').filter({ hasText: `${successor.familyNames}, ${successor.givenNames}` });
      await successorButton.waitFor();
      await successorButton.click();
    }
  }
  if (!pet) {
    const [response] = await Promise.all([
      page.waitForResponse((candidate) => candidate.request().method() === 'POST'
        && candidate.url().endsWith(`/api/v1/owners/${owner.id}/pets`)),
      (async () => {
        await page.getByLabel('Nombre', { exact: true }).fill(petFixture.name);
        await page.getByLabel('Especie', { exact: true }).fill(petFixture.species);
        await page.getByLabel('Raza', { exact: true }).fill(petFixture.breed);
        await page.getByLabel('o edad estimada (meses)', { exact: true }).fill(String(petFixture.estimatedAgeMonths));
        await page.getByRole('button', { name: 'Crear ficha de mascota' }).click();
      })(),
    ]);
    assert.equal(response.status(), 201, 'the pet form must persist through the authenticated API');
    pet = (await response.json()).data;
  } else {
    const petButton = page.locator('.pet-list button').filter({ hasText: petFixture.name });
    await petButton.waitFor();
    await petButton.click();
  }

  await page.getByRole('heading', { name: petFixture.name }).waitFor();
  await page.getByRole('heading', { name: 'Historia clínica protegida' }).waitFor();
  const storedPet = await browserFetch(page, `/api/v1/pets/${pet.id}`);
  assert.equal(storedPet.status, 200, 'the persisted pet must be readable after the browser form completes');
  assert.equal(storedPet.body?.data?.currentOwnerId, owner.id, 'the pet must retain its owner association');
  const deniedHistory = await browserFetch(page, `/api/v1/pets/${pet.id}/clinical-episodes`);
  assert.equal(deniedHistory.status, 403, 'an administrator without VETERINARIO must not read the clinical history');

  if (storedPet.body?.data?.currentOwnerId !== successor.id) {
    const [response] = await Promise.all([
      page.waitForResponse((candidate) => candidate.request().method() === 'POST'
        && candidate.url().endsWith(`/api/v1/pets/${pet.id}/owner-transfers`)),
      (async () => {
        await page.locator('#transfer-owner').selectOption(successor.id);
        await page.locator('#transfer-reason').fill('Synthetic verified ownership transfer');
        await page.locator('#transfer-confirmed').check();
        await page.locator('.ownership-layout button[type="submit"]').click();
      })(),
    ]);
    assert.equal(response.status(), 200, 'the verified transfer must persist through the authenticated API');
  }
  const ownership = await browserFetch(page, `/api/v1/pets/${pet.id}/ownership-periods`);
  assert.equal(ownership.status, 200, 'an authorized administrator must read the ownership trace');
  assert.equal(ownership.body?.data?.length, 2, 'the transfer must preserve exactly the previous and current periods');
  assert.equal(ownership.body?.data?.filter((period) => period.effectiveUntil === null).length, 1,
    'the transfer must retain exactly one current owner');
  assert.equal(ownership.body?.data?.find((period) => period.effectiveUntil === null)?.ownerId, successor.id,
    'the synthetic fixture must finish under the verified successor');

  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false,
    'the B02 page must not overflow horizontally on a narrow viewport');
  await page.evaluate(() => {
    const focused = document.activeElement;
    if (focused instanceof HTMLElement) {
      focused.blur();
    }
    window.scrollTo(0, 0);
  });
  await captureEvidence(page, 'b02-owner-pet-mobile.png');
  await page.setViewportSize({ width: 1280, height: 900 });

  await page.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  await context.close();
  process.stdout.write('B02_OWNER_PET_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function login(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
}

async function existingOwner(page) {
  const response = await browserFetch(page, `/api/v1/owners?query=${encodeURIComponent(ownerFixture.givenNames)}`);
  assert.equal(response.status, 200, 'the authenticated administrator must list owners in its tenant');
  return response.body?.data?.find((owner) => owner.givenNames === ownerFixture.givenNames
    && owner.familyNames === ownerFixture.familyNames) ?? null;
}

async function ensureSuccessor(page) {
  const response = await browserFetch(page, `/api/v1/owners?query=${encodeURIComponent(successorFixture.givenNames)}`);
  assert.equal(response.status, 200, 'the authenticated administrator must list the transfer successor');
  const existing = response.body?.data?.find((owner) => owner.givenNames === successorFixture.givenNames
    && owner.familyNames === successorFixture.familyNames);
  if (existing) {
    return existing;
  }
  const [created] = await Promise.all([
    page.waitForResponse((candidate) => candidate.request().method() === 'POST'
      && candidate.url().endsWith('/api/v1/owners')),
    (async () => {
      await page.locator('#owner-given-names').fill(successorFixture.givenNames);
      await page.locator('#owner-family-names').fill(successorFixture.familyNames);
      await page.locator('#owner-phone').fill(successorFixture.phone);
      await page.locator('#owner-address').fill(successorFixture.address);
      await page.locator('.owner-panel form button[type="submit"]').click();
    })(),
  ]);
  assert.equal(created.status(), 201, 'the successor owner form must persist through the authenticated API');
  return (await created.json()).data;
}

async function existingPet(page, ownerId) {
  const response = await browserFetch(page, `/api/v1/owners/${ownerId}/pets`);
  assert.equal(response.status, 200, 'the selected owner must expose only its tenant pets');
  return response.body?.data?.find((pet) => pet.name === petFixture.name) ?? null;
}

async function browserFetch(page, path, options = {}) {
  return page.evaluate(async ({ path, options }) => {
    const response = await fetch(path, options);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, { path, options });
}

async function captureEvidence(page, filename) {
  if (evidenceDirectory) {
    await page.screenshot({ path: resolve(evidenceDirectory, filename), fullPage: true });
  }
}
