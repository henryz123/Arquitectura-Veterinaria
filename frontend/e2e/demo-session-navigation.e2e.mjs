import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = [
  'AV_DEMO_AURORA_PASSWORD',
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
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await loginClient(clientPage);

  const clientSession = await browserFetch(clientPage, '/api/v1/session-context');
  assert.equal(clientSession.status, 200, 'the browser must recover the authenticated CLIENT context');
  assert.equal(clientSession.body?.data?.accountType, 'CLIENT', 'the client login must establish a CLIENT session');

  await clientPage.getByRole('button', { name: 'Registrar mascota' }).first().click();
  const petForm = clientPage.locator('#client-register-pet form');
  await petForm.getByRole('button', { name: 'Registrar mascota' }).click();
  await petForm.getByText('Indica el nombre de la mascota.').waitFor();
  await petForm.getByText('Indica la especie.').waitFor();
  await petForm.getByText('Indica una fecha de nacimiento o una edad estimada para la mascota.').waitFor();
  await petForm.locator('input[name="newPetName"]').fill('Validación local');
  await petForm.locator('input[name="newPetSpecies"]').fill('Canino');
  await petForm.locator('input[name="newPetBirthDate"]').fill('2024-01-01');
  await petForm.locator('input[name="newPetEstimatedAge"]').fill('12');
  await petForm.getByRole('button', { name: 'Registrar mascota' }).click();
  await petForm.getByText('Indica una fecha de nacimiento o una edad estimada, pero no ambas.').waitFor();

  await clientPage.getByRole('button', { name: 'Mis datos' }).click();
  const profileForm = clientPage.locator('#client-profile form');
  await profileForm.getByLabel('Teléfono').fill('ABC');
  await profileForm.getByRole('button', { name: 'Guardar datos basicos' }).click();
  await profileForm.getByText(/Usa solo números/).waitFor();
  await clientPage.screenshot({ path: '__screenshots__/validacion-client-final.png', fullPage: true });

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
  await staffPage.getByRole('link', { name: 'Productos e inventario' }).waitFor();
  await staffPage.getByRole('heading', { name: 'Información de la veterinaria' }).waitFor();
  await staffPage.screenshot({ path: '__screenshots__/inicio-admin-final.png', fullPage: true });

  await staffPage.getByRole('link', { name: 'Productos e inventario' }).click();
  await staffPage.waitForURL(`${baseUrl}/inventory`);
  const productForm = staffPage.locator('section[aria-labelledby="product-title"] form');
  await productForm.locator('#product-sku').fill('UI-VALIDATION');
  await productForm.locator('#product-title-input').fill('Producto de validación');
  await productForm.locator('#product-price').fill('-1');
  await productForm.getByRole('button', { name: 'Registrar producto' }).click();
  await productForm.getByText('Indica un precio no negativo.').waitFor();
  const stockForm = staffPage.locator('form.stock-form');
  await stockForm.locator('#stock-quantity').fill('0');
  await stockForm.locator('#stock-reason').fill('Validación sin persistencia');
  await stockForm.getByRole('button', { name: 'Registrar movimiento' }).click();
  await stockForm.getByText('Indica una cantidad entera distinta de cero.').waitFor();
  await staffPage.screenshot({ path: '__screenshots__/validacion-inventario-final.png', fullPage: true });

  await staffPage.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await staffPage.getByRole('link', { name: 'Citas y agenda' }).click();
  await staffPage.waitForURL(`${baseUrl}/schedule`);
  await staffPage.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();
  await staffPage.locator('section.overview-grid').waitFor();
  await assertSchedulePresentation(staffPage);
  await staffPage.screenshot({ path: '__screenshots__/agenda-admin-final.png', fullPage: true });
  await staffPage.reload({ waitUntil: 'networkidle' });
  await staffPage.getByRole('heading', { name: 'Preparar horarios reales' }).waitFor();
  await staffPage.locator('section.overview-grid').waitFor();
  await assertSchedulePresentation(staffPage);

  await staffPage.goto(`${baseUrl}/workspace`, { waitUntil: 'networkidle' });
  await staffPage.getByRole('link', { name: 'Equipo y roles' }).click();
  await staffPage.waitForURL(`${baseUrl}/workspace#team`);
  await staffPage.locator('#team').waitFor();
  assert.equal(await staffPage.getByRole('link', { name: 'Equipo y roles' }).evaluate((link) => link.classList.contains('is-active')), true,
    'the team navigation item must visibly identify the active administration section');
  await staffPage.screenshot({ path: '__screenshots__/equipo-roles-admin-final.png', fullPage: true });
  await staffPage.getByRole('link', { name: 'Nueva invitación' }).click();
  await staffPage.waitForURL(`${baseUrl}/workspace#invitation`);
  await staffPage.getByLabel('Correo institucional').waitFor();
  assert.equal(await staffPage.getByRole('link', { name: 'Nueva invitación' }).evaluate((link) => link.classList.contains('is-active')), true,
    'the invitation navigation item must open and identify its existing form');
  await staffPage.screenshot({ path: '__screenshots__/nueva-invitacion-admin-final.png', fullPage: true });
  await staffPage.getByRole('link', { name: 'Organización' }).click();
  await staffPage.waitForURL(`${baseUrl}/workspace#organization`);
  await staffPage.locator('#organization').waitFor();
  assert.equal(await staffPage.getByRole('link', { name: 'Organización' }).evaluate((link) => link.classList.contains('is-active')), true,
    'the organization navigation item must visibly identify the active section');
  assert.equal(await staffPage.locator('text=Opciones disponibles para tu cuenta').count(), 0,
    'the administrator must not render the restricted workspace state');
  await staffPage.screenshot({ path: '__screenshots__/organizacion-admin-final.png', fullPage: true });

  await staffPage.reload({ waitUntil: 'networkidle' });
  await staffPage.waitForURL(`${baseUrl}/workspace#organization`);
  await staffPage.getByRole('link', { name: 'Productos e inventario' }).waitFor();
  await staffContext.close();

  const veterinarianContext = await browser.newContext();
  const veterinarianPage = await veterinarianContext.newPage();
  await loginStaff(veterinarianPage, process.env.AV_DEMO_VETERINARIO_AURORA_EMAIL,
    process.env.AV_DEMO_VETERINARIO_AURORA_PASSWORD);
  await veterinarianPage.getByRole('link', { name: 'Organización' }).click();
  await veterinarianPage.locator('#organization').waitFor();
  await veterinarianPage.getByRole('heading', { name: 'Información de la veterinaria' }).waitFor();
  assert.equal(await veterinarianPage.getByRole('link', { name: 'Citas y agenda' }).count(), 0,
    'a VETERINARIO must not receive scheduling management access through navigation');
  assert.equal(await veterinarianPage.getByRole('link', { name: 'Productos e inventario' }).count(), 0,
    'a VETERINARIO must not receive catalog administration access through navigation');
  await veterinarianContext.close();

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

async function loginStaff(page, email = staffEmail, password = process.env.AV_DEMO_AURORA_PASSWORD) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(email);
  await page.getByLabel(/Contrase/).fill(password);
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

async function assertSchedulePresentation(page) {
  const presentation = await page.evaluate(() => {
    const shell = document.querySelector('.schedule-shell');
    const sidebar = document.querySelector('.schedule-shell .sidebar');
    const refresh = document.querySelector('.schedule-shell .button-secondary');
    const navigation = Array.from(document.querySelectorAll('.schedule-shell .sidebar nav a'))
      .find((link) => link.textContent?.includes('Citas y agenda'));
    if (!shell || !sidebar || !refresh || !navigation) {
      return null;
    }
    return {
      shellDisplay: getComputedStyle(shell).display,
      shellColumns: getComputedStyle(shell).gridTemplateColumns,
      sidebarBackground: getComputedStyle(sidebar).backgroundColor,
      refreshBorder: getComputedStyle(refresh).borderTopStyle,
      refreshRadius: getComputedStyle(refresh).borderRadius,
      navigationDecoration: getComputedStyle(navigation).textDecorationLine,
    };
  });
  assert.ok(presentation, 'the schedule must render the staff shell and its controls');
  assert.equal(presentation.shellDisplay, 'grid', 'the schedule must use the shared staff grid layout');
  assert.notEqual(presentation.shellColumns, 'none', 'the schedule shell must have resolved layout columns');
  assert.notEqual(presentation.sidebarBackground, 'rgba(0, 0, 0, 0)', 'the schedule sidebar must receive the shared staff background');
  assert.equal(presentation.refreshBorder, 'solid', 'the schedule action must receive the shared button border styling');
  assert.notEqual(presentation.refreshRadius, '0px', 'the schedule actions must receive the system button shape');
  assert.equal(presentation.navigationDecoration, 'none', 'the schedule navigation must not fall back to browser-default link decoration');
}
