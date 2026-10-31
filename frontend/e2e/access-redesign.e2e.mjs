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
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  await verifyTenantSelectorAndProfileSwitching();
  await verifyStaffLoginAndSessionRestore();
  await verifyClientLoginAndTenantIsolation();
  await verifyBosqueClientLoginWhenConfigured();
  await verifyRegistrationAndRecoveryNavigation();
  await captureResponsiveAccessScreens();
  process.stdout.write('ACCESS_REDESIGN_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function verifyTenantSelectorAndProfileSwitching() {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${baseUrl}/seleccionar-veterinaria`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Selecciona tu veterinaria' }).waitFor();
  assert.equal(await page.locator('.tenant-option').count(), 2, 'the selector must expose exactly Aurora and Bosque');

  await page.locator('.tenant-option').filter({ hasText: 'Veterinaria Aurora' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-aurora`);
  await page.getByRole('heading', { name: 'Acceso a la plataforma' }).waitFor();
  await page.getByText('Veterinaria Aurora', { exact: true }).first().waitFor();
  assert.equal(await page.getByRole('link', { name: 'Personal' }).getAttribute('aria-current'), 'page');

  await page.getByLabel('Contraseña', { exact: true }).fill('NoDebeConservarse123');
  await page.getByRole('link', { name: 'Cliente' }).click();
  await page.waitForURL(`${baseUrl}/client/login/veterinaria-aurora`);
  assert.equal(await page.getByLabel('Contraseña', { exact: true }).inputValue(), '', 'switching account type must not retain a password');
  assert.equal(await page.getByRole('link', { name: 'Cliente' }).getAttribute('aria-current'), 'page');

  await page.getByRole('link', { name: 'Cambiar veterinaria' }).click();
  await page.waitForURL(`${baseUrl}/seleccionar-veterinaria`);
  await page.locator('.tenant-option').filter({ hasText: 'Veterinaria Bosque' }).click();
  await page.waitForURL(`${baseUrl}/login/veterinaria-bosque`);
  await page.getByText('Veterinaria Bosque', { exact: true }).first().waitFor();
  await page.getByRole('link', { name: 'Cliente' }).click();
  await page.waitForURL(`${baseUrl}/client/login/veterinaria-bosque`);
  assert.equal(await page.getByRole('link', { name: 'Cliente' }).getAttribute('aria-current'), 'page');
  await context.close();
}

async function verifyStaffLoginAndSessionRestore() {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.AV_DEMO_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(`${baseUrl}/workspace`);
  await page.reload({ waitUntil: 'networkidle' });
  assert.match(page.url(), /\/workspace$/, 'staff must keep an active session after reload');
  await context.close();
}

async function verifyClientLoginAndTenantIsolation() {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo de cliente').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
  await page.reload({ waitUntil: 'networkidle' });
  assert.match(page.url(), /\/client-space$/, 'client must keep an active session after reload');

  await page.goto(`${baseUrl}/client/login/veterinaria-bosque`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo de cliente').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.locator('.access-error').waitFor();
  assert.equal(await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).isEnabled(), true,
    'an invalid login must release the submit button');
  await context.close();
}

async function verifyBosqueClientLoginWhenConfigured() {
  if (!process.env.AV_DEMO_CLIENT_BOSQUE_EMAIL || !process.env.AV_DEMO_CLIENT_BOSQUE_PASSWORD) {
    return;
  }
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${baseUrl}/client/login/veterinaria-bosque`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo de cliente').fill(process.env.AV_DEMO_CLIENT_BOSQUE_EMAIL);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.AV_DEMO_CLIENT_BOSQUE_PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
  await page.reload({ waitUntil: 'networkidle' });
  assert.match(page.url(), /\/client-space$/, 'Bosque client must keep an active session after reload');
  await context.close();
}

async function verifyRegistrationAndRecoveryNavigation() {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByRole('link', { name: 'Crear cuenta' }).click();
  await page.waitForURL(`${baseUrl}/client/register/veterinaria-aurora`);
  await page.getByText('Esta cuenta quedará asociada a Veterinaria Aurora.').waitFor();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await page.getByText('Ingresa un correo válido.').waitFor();

  await page.getByRole('link', { name: 'Volver al inicio de sesión' }).click();
  await page.waitForURL(`${baseUrl}/client/login/veterinaria-aurora`);
  await page.getByRole('link', { name: 'Recuperar acceso' }).click();
  await page.waitForURL(`${baseUrl}/client/recover/veterinaria-aurora`);
  await page.getByText('Por privacidad, el resultado no confirma si el correo está registrado.').waitFor();
  await context.close();
}

async function captureResponsiveAccessScreens() {
  const captureTargets = {
    selector: '/seleccionar-veterinaria',
    'aurora-staff': '/login/veterinaria-aurora',
    'aurora-client': '/client/login/veterinaria-aurora',
    'bosque-client': '/client/login/veterinaria-bosque',
    register: '/client/register/veterinaria-aurora',
    recovery: '/client/recover/veterinaria-aurora',
  };
  for (const [name, path] of Object.entries(captureTargets)) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    await page.goto(`${baseUrl}${path}`, { waitUntil: 'networkidle' });
    await page.getByRole('heading').waitFor();
    await page.screenshot({ path: `__screenshots__/access-redesign-${name}.png`, fullPage: true });
    await context.close();
  }

  for (const [name, viewport] of Object.entries({
    desktop: { width: 1440, height: 900 },
    tablet: { width: 768, height: 1024 },
    mobile: { width: 390, height: 844 },
  })) {
    const context = await browser.newContext({ viewport });
    const page = await context.newPage();
    await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Acceso a la plataforma' }).waitFor();
    const passwordToggle = page.getByRole('button', { name: 'Mostrar contraseña' });
    const passwordInput = page.getByLabel('Contraseña', { exact: true });
    assert.equal(await passwordToggle.count(), 1, `${name} must expose one password visibility control`);
    assert.equal(await passwordInput.count(), 1, `${name} must expose one password field`);
    if (name === 'desktop') {
      await passwordToggle.focus();
      await page.keyboard.press('Enter');
    } else {
      await passwordToggle.click();
    }
    const hidePasswordToggle = page.getByRole('button', { name: 'Ocultar contraseña' });
    assert.equal(await hidePasswordToggle.count(), 1, `${name} must update the password toggle label`);
    assert.equal(await passwordInput.getAttribute('type'), 'text', `${name} must expose the password with the keyboard`);
    assert.equal(await hidePasswordToggle.getAttribute('aria-pressed'), 'true', `${name} must announce the visible password state`);
    await hidePasswordToggle.click();
    assert.equal(await passwordInput.getAttribute('type'), 'password', `${name} must restore hidden password state`);
    const metrics = await page.evaluate(() => ({
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
      gridColumns: getComputedStyle(document.querySelector('.access-page')).gridTemplateColumns,
      input: document.querySelector('.access-password-field input')?.getBoundingClientRect(),
      toggle: document.querySelector('.access-password-toggle')?.getBoundingClientRect(),
    }));
    assert.ok(metrics.documentWidth <= metrics.viewportWidth, `${name} must not introduce horizontal scroll`);
    assert.ok(metrics.input && metrics.toggle, `${name} must render the password control`);
    assert.equal(
      Math.round((metrics.toggle.top + metrics.toggle.bottom - metrics.input.top - metrics.input.bottom) / 2),
      0,
      `${name} must vertically center the password visibility control`,
    );
    if (name === 'mobile') {
      assert.equal(metrics.gridColumns.split(' ').length, 1, 'mobile must collapse the access layout to one column');
    }
    await page.screenshot({ path: `__screenshots__/access-redesign-${name}.png`, fullPage: true });
    await context.close();
  }
}
