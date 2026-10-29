import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
  'AV_DEMO_CLIENT_AURORA_EMAIL',
  'AV_DEMO_CLIENT_AURORA_PASSWORD',
  'AV_DEMO_CLIENT_BOSQUE_EMAIL',
  'AV_DEMO_CLIENT_BOSQUE_PASSWORD',
  'AV_E2E_CHROME_PATH',
];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required local E2E environment variable: ${key}`);
  }
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const linkOwnerFixture = {
  givenNames: 'CLIENT B02',
  familyNames: 'Aurora',
  phone: '900000080',
  address: 'Responsable sintético de vinculación CLIENT',
};
const successorFixture = {
  givenNames: 'CLIENT Transferencia',
  familyNames: 'Aurora',
  phone: '900000081',
  address: 'Responsable sintético alternativo',
};
const editablePetFixture = {
  name: 'CLIENT Ficha E2E',
  species: 'Canino',
  breed: 'Mestizo',
  sex: 'HEMBRA',
  estimatedAgeMonths: 30,
  distinguishingMarks: 'Ficha básica sintética editable',
};
const transferPetFixture = {
  name: 'CLIENT Revocación E2E',
  species: 'Felino',
  breed: 'Mestizo',
  sex: 'MACHO',
  estimatedAgeMonths: 18,
  distinguishingMarks: 'Ficha sintética para probar revocación',
};

const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });
let auroraContext;
let bosqueContext;
let staffContext;

try {
  auroraContext = await browser.newContext();
  const auroraPage = await auroraContext.newPage();
  await loginClient(auroraPage, 'veterinaria-aurora', process.env.AV_DEMO_CLIENT_AURORA_EMAIL,
    process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);

  let linkStatus = await clientLinkStatus(auroraPage);
  if (linkStatus.status === 'SIN_SOLICITUD' || linkStatus.status === 'RECHAZADA') {
    const linkRequested = auroraPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/owner-link-requests');
    await auroraPage.getByRole('button', { name: 'Solicitar vinculacion manual' }).click();
    assert.equal((await linkRequested).status(), 201, 'the client UI must create a manual link request');
    linkStatus = await clientLinkStatus(auroraPage);
  }

  if (linkStatus.status === 'PENDIENTE') {
    assert.ok(linkStatus.requestReference, 'a pending request must retain an opaque reference for authorized staff');
    await logoutClient(auroraPage, 'veterinaria-aurora');
    staffContext = await browser.newContext();
    const staffPage = await staffContext.newPage();
    await loginStaffCare(staffPage);
    const linkOwner = await ensureOwner(staffPage, linkOwnerFixture);
    await approveLinkRequest(staffPage, linkStatus.requestReference, linkOwner.id);
    await staffContext.close();
    staffContext = undefined;
    await loginClient(auroraPage, 'veterinaria-aurora', process.env.AV_DEMO_CLIENT_AURORA_EMAIL,
      process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  }

  linkStatus = await clientLinkStatus(auroraPage);
  assert.equal(linkStatus.status, 'APROBADA', 'the client must obtain data access only after explicit staff approval');

  const restoredContext = auroraPage.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/session-context');
  await auroraPage.reload({ waitUntil: 'networkidle' });
  assert.equal((await restoredContext).status(), 200, 'the client session must recover after a browser reload');
  await auroraPage.getByRole('button', { name: 'Mis datos' }).click();
  await auroraPage.getByRole('heading', { name: 'Mis datos' }).waitFor();

  const ownerProfile = await browserFetch(auroraPage, '/api/v1/client/owner-profile');
  assert.equal(ownerProfile.status, 200, 'an approved client must read only its own basic profile');
  const linkedOwner = ownerProfile.body?.data;
  assert.ok(linkedOwner?.id, 'the approved client must resolve one tenant-local owner profile');

  const profileSaved = auroraPage.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === '/api/v1/client/owner-profile');
  await auroraPage.locator('input[name="address"]').fill('Contacto CLIENT sintético verificado');
  await auroraPage.getByRole('button', { name: 'Guardar datos basicos' }).click();
  assert.equal((await profileSaved).status(), 200, 'the client UI must persist its own basic contact profile');

  let editablePet = await findClientPet(auroraPage, editablePetFixture.name);
  if (!editablePet) {
    await auroraPage.getByRole('button', { name: 'Registrar mascota' }).click();
    const petCreated = auroraPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/pets');
    await auroraPage.locator('input[name="newPetName"]').fill(editablePetFixture.name);
    await auroraPage.locator('input[name="newPetSpecies"]').fill(editablePetFixture.species);
    await auroraPage.locator('input[name="newPetBreed"]').fill(editablePetFixture.breed);
    await auroraPage.locator('select[name="newPetSex"]').selectOption(editablePetFixture.sex);
    await auroraPage.locator('input[name="newPetEstimatedAge"]').fill(String(editablePetFixture.estimatedAgeMonths));
    await auroraPage.locator('input[name="newPetMarks"]').fill(editablePetFixture.distinguishingMarks);
    await auroraPage.locator('#client-register-pet').getByRole('button', { name: 'Registrar mascota' }).click();
    assert.equal((await petCreated).status(), 201, 'the client UI must persist a basic pet under its current responsibility');
    editablePet = await findClientPet(auroraPage, editablePetFixture.name);
  }
  assert.ok(editablePet?.id, 'the editable pet fixture must be tenant-local and visible to its client');

  await auroraPage.getByRole('button', { name: 'Mis mascotas' }).click();
  const editableCard = auroraPage.locator('.pet-cards article').filter({ hasText: editablePetFixture.name });
  await editableCard.getByRole('button', { name: 'Editar ficha basica' }).click();
  const petSaved = auroraPage.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/v1/client/pets/${editablePet.id}`);
  await auroraPage.locator('input[name="editPetMarks"]').fill('Ficha CLIENT actualizada mediante navegador');
  await auroraPage.getByRole('button', { name: 'Guardar ficha' }).click();
  assert.equal((await petSaved).status(), 200, 'the client UI must edit only its own basic pet data');

  const persistedOwnPet = await browserFetch(auroraPage, `/api/v1/client/pets/${editablePet.id}`);
  assert.equal(persistedOwnPet.status, 200, 'the edited client pet must persist through the authenticated API');
  assert.equal(Object.hasOwn(persistedOwnPet.body?.data ?? {}, 'clinicalEpisodes'), false,
    'the client pet projection must not expose clinical history');
  const deniedClinicalHistory = await browserFetch(auroraPage, `/api/v1/pets/${editablePet.id}/clinical-episodes`);
  assert.equal(deniedClinicalHistory.status, 403, 'a client must not access the full clinical history endpoint');

  await logoutClient(auroraPage, 'veterinaria-aurora');
  const loggedOutSpace = await browserFetch(auroraPage, '/api/v1/client-space');
  assert.equal(loggedOutSpace.status, 401, 'client space must reject access after logout');

  staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaffCare(staffPage);
  const successor = await ensureOwner(staffPage, successorFixture);
  const transferPet = await ensureTransferPet(staffPage, linkedOwner, successor);
  await staffContext.close();
  staffContext = undefined;

  await loginClient(auroraPage, 'veterinaria-aurora', process.env.AV_DEMO_CLIENT_AURORA_EMAIL,
    process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  const revokedPet = await browserFetch(auroraPage, `/api/v1/client/pets/${transferPet.id}`);
  assert.equal(revokedPet.status, 404, 'the former client owner must lose access after the verified transfer');
  assert.equal((await browserFetch(auroraPage, `/api/v1/client/pets/${editablePet.id}`)).status, 200,
    'a transfer must not revoke the client\'s separate current pet');
  await logoutClient(auroraPage, 'veterinaria-aurora');

  bosqueContext = await browser.newContext();
  const bosquePage = await bosqueContext.newPage();
  await loginClient(bosquePage, 'veterinaria-bosque', process.env.AV_DEMO_CLIENT_BOSQUE_EMAIL,
    process.env.AV_DEMO_CLIENT_BOSQUE_PASSWORD);
  const crossTenantPet = await browserFetch(bosquePage, `/api/v1/client/pets/${editablePet.id}`);
  assert.equal(crossTenantPet.status, 404, 'a Bosque client must not resolve an Aurora pet identifier');
  await logoutClient(bosquePage, 'veterinaria-bosque');

  process.stdout.write('CLIENT_B02_BROWSER_E2E=PASS\n');
} finally {
  await staffContext?.close();
  await bosqueContext?.close();
  await auroraContext?.close();
  await browser.close();
}

async function loginClient(page, tenantSlug, email, password) {
  await page.goto(`${baseUrl}/client/login/${tenantSlug}`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
}

async function logoutClient(page, tenantSlug) {
  await page.getByRole('button', { name: 'Cerrar sesion' }).click();
  await page.waitForURL(`${baseUrl}/client/login/${tenantSlug}`);
}

async function loginStaffCare(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
  await page.goto(`${baseUrl}/care`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Clientes y mascotas' }).waitFor();
}

async function clientLinkStatus(page) {
  const response = await browserFetch(page, '/api/v1/client/owner-link-status');
  assert.equal(response.status, 200, 'the client must read only its own link status');
  return response.body?.data;
}

async function findClientPet(page, name) {
  const response = await browserFetch(page, '/api/v1/client/pets');
  assert.equal(response.status, 200, 'the client must list only current basic pet profiles');
  return response.body?.data?.find((pet) => pet.name === name) ?? null;
}

async function ensureOwner(page, fixture) {
  const listed = await browserFetch(page, `/api/v1/owners?query=${encodeURIComponent(fixture.givenNames)}`);
  assert.equal(listed.status, 200, 'authorized staff must list owners in its tenant');
  const existing = listed.body?.data?.find((owner) => owner.givenNames === fixture.givenNames
    && owner.familyNames === fixture.familyNames && owner.phone === fixture.phone);
  if (existing) {
    return existing;
  }
  const created = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/owners');
  await page.locator('#owner-given-names').fill(fixture.givenNames);
  await page.locator('#owner-family-names').fill(fixture.familyNames);
  await page.locator('#owner-phone').fill(fixture.phone);
  await page.locator('#owner-address').fill(fixture.address);
  await page.getByRole('button', { name: 'Registrar propietario' }).click();
  assert.equal((await created).status(), 201, 'staff must create the local owner through the visible form');
  const afterCreate = await browserFetch(page, `/api/v1/owners?query=${encodeURIComponent(fixture.givenNames)}`);
  return afterCreate.body?.data?.find((owner) => owner.givenNames === fixture.givenNames
    && owner.familyNames === fixture.familyNames && owner.phone === fixture.phone) ?? null;
}

async function approveLinkRequest(page, requestReference, ownerId) {
  await page.getByRole('button', { name: requestReference }).click();
  await page.locator('#link-owner').selectOption(ownerId);
  await page.locator('#link-method').fill('Verificación presencial sintética');
  await page.locator('#link-confirmed').check();
  const resolved = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith('/resolution')
    && new URL(response.url()).pathname.includes('/owner-link-requests/'));
  await page.getByRole('button', { name: 'Resolver solicitud' }).click();
  assert.equal((await resolved).status(), 200, 'staff approval must be an explicit verified UI action');
}

async function ensureTransferPet(page, sourceOwner, successor) {
  const sourcePets = await browserFetch(page, `/api/v1/owners/${sourceOwner.id}/pets`);
  assert.equal(sourcePets.status, 200, 'staff must read the linked owner pets in the same tenant');
  let transferPet = sourcePets.body?.data?.find((pet) => pet.name === transferPetFixture.name) ?? null;
  if (!transferPet) {
    const successorPets = await browserFetch(page, `/api/v1/owners/${successor.id}/pets`);
    transferPet = successorPets.body?.data?.find((pet) => pet.name === transferPetFixture.name) ?? null;
  }
  if (!transferPet) {
    await selectOwner(page, sourceOwner);
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/v1/owners/${sourceOwner.id}/pets`);
    await page.locator('#pet-name').fill(transferPetFixture.name);
    await page.locator('#pet-species').fill(transferPetFixture.species);
    await page.locator('#pet-breed').fill(transferPetFixture.breed);
    await page.locator('#pet-sex').selectOption(transferPetFixture.sex);
    await page.locator('#pet-age').fill(String(transferPetFixture.estimatedAgeMonths));
    await page.locator('#pet-marks').fill(transferPetFixture.distinguishingMarks);
    await page.getByRole('button', { name: 'Crear ficha de mascota' }).click();
    assert.equal((await created).status(), 201, 'staff must create the transfer fixture through the visible form');
    transferPet = (await browserFetch(page, `/api/v1/owners/${sourceOwner.id}/pets`)).body?.data
      ?.find((pet) => pet.name === transferPetFixture.name) ?? null;
  }
  assert.ok(transferPet?.id, 'the transfer fixture must be persisted before an ownership change');

  if (transferPet.currentOwnerId === sourceOwner.id) {
    await selectOwner(page, sourceOwner);
    await page.locator('.pet-list button').filter({ hasText: transferPetFixture.name }).click();
    await page.locator('#transfer-owner').selectOption(successor.id);
    await page.locator('#transfer-reason').fill('Transferencia sintética verificada para comprobar revocación CLIENT.');
    await page.locator('#transfer-confirmed').check();
    const transferred = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/v1/pets/${transferPet.id}/owner-transfers`);
    await page.getByRole('button', { name: 'Transferir responsable' }).click();
    assert.equal((await transferred).status(), 200, 'the staff UI must persist the verified transfer');
  }
  return transferPet;
}

async function selectOwner(page, owner) {
  const label = `${owner.familyNames}, ${owner.givenNames}`;
  const loaded = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/owners/${owner.id}/pets`);
  await page.locator('.owner-list button').filter({ hasText: label }).first().click();
  await loaded;
}

async function browserFetch(page, path, options = {}) {
  return page.evaluate(async ({ path, options }) => {
    const response = await fetch(path, options);
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }, { path, options });
}
