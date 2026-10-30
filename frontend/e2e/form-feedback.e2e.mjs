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
  const clientContext = await browser.newContext();
  const clientPage = await clientContext.newPage();
  await loginClient(clientPage);

  await clientPage.getByRole('button', { name: 'Registrar mascota' }).click();
  const petForm = clientPage.locator('#client-register-pet form');
  await petForm.getByRole('button', { name: 'Registrar mascota' }).click();
  await petForm.getByText('Indica el nombre de la mascota.').waitFor();
  await petForm.getByText('Indica la especie.').waitFor();
  await petForm.getByText('Indica una fecha de nacimiento o una edad estimada para la mascota.').waitFor();
  assert.equal(await petForm.locator('input[name="newPetName"]').getAttribute('aria-invalid'), 'true');
  await clientPage.getByRole('status').filter({ hasText: 'Completa los campos indicados antes de registrar la mascota.' }).waitFor();

  await petForm.locator('input[name="newPetName"]').fill('Mascota de validación');
  await petForm.locator('input[name="newPetSpecies"]').fill('Canino');
  await petForm.locator('input[name="newPetBirthDate"]').fill('2024-01-01');
  await petForm.locator('input[name="newPetEstimatedAge"]').fill('12');
  await petForm.getByText('Indica una fecha de nacimiento o una edad estimada, pero no ambas.').waitFor();
  await clientPage.screenshot({ path: '__screenshots__/form-feedback-client-invalid.png', fullPage: true });

  await clientPage.getByRole('button', { name: 'Mis datos' }).click();
  const profileForm = clientPage.locator('#client-profile form');
  const phone = profileForm.getByLabel('Teléfono');
  const originalPhone = await phone.inputValue();
  await phone.fill('ABC');
  await profileForm.getByRole('button', { name: 'Guardar datos basicos' }).click();
  await profileForm.getByText(/Usa entre 5 y 40 caracteres/).waitFor();
  await clientPage.getByRole('status').filter({ hasText: 'Completa los campos indicados antes de guardar tus datos.' }).waitFor();

  await phone.fill('1234');
  await phone.press('Tab');
  await profileForm.getByText(/Usa entre 5 y 40 caracteres/).waitFor();
  assert.equal(await phone.getAttribute('aria-invalid'), 'true', 'a phone shorter than the approved format must be marked invalid');

  await phone.fill(originalPhone);
  const updated = clientPage.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === '/api/v1/client/owner-profile');
  await profileForm.getByRole('button', { name: 'Guardar datos basicos' }).click();
  assert.equal((await updated).status(), 200, 'the valid profile update must be confirmed by the backend');
  await clientPage.getByRole('status').filter({ hasText: 'Tus datos basicos fueron actualizados.' }).waitFor();

  await clientPage.route('**/api/v1/client/owner-profile', (route) => route.abort('failed'));
  await profileForm.getByRole('button', { name: 'Guardar datos basicos' }).click();
  await clientPage.getByRole('alert').filter({ hasText: 'No fue posible conectar con el servidor.' }).waitFor();
  await clientPage.unroute('**/api/v1/client/owner-profile');
  assert.equal((await browserFetch(clientPage, '/api/v1/session-context')).status, 200,
    'the controlled request failure must not invalidate the client session');
  await clientContext.close();

  const staffContext = await browser.newContext();
  const staffPage = await staffContext.newPage();
  await loginStaff(staffPage);
  await staffPage.getByRole('link', { name: 'Nueva invitación' }).click();
  const invitation = staffPage.getByLabel('Correo institucional');
  await invitation.fill('correo-invalido');
  await invitation.press('Tab');
  await staffPage.getByText('Ingresa un correo válido para la cuenta laboral.').waitFor();
  assert.equal(await invitation.getAttribute('aria-invalid'), 'true');
  await staffPage.getByRole('button', { name: 'Registrar invitación' }).click();
  await staffPage.getByRole('status').filter({ hasText: 'Completa el correo y al menos un rol antes de registrar la invitación.' }).waitFor();

  await staffPage.getByRole('link', { name: 'Productos e inventario' }).click();
  await staffPage.waitForURL(`${baseUrl}/inventory`);
  const productForm = staffPage.locator('form').filter({ has: staffPage.locator('#product-sku') });
  await productForm.getByRole('button', { name: 'Registrar producto' }).click();
  await productForm.getByText('Indica un SKU de hasta 64 caracteres.').waitFor();
  await productForm.getByText('Indica el nombre del producto.').waitFor();
  await staffPage.getByRole('status').filter({ hasText: 'Completa los campos indicados antes de guardar el producto.' }).waitFor();

  const suffix = Date.now().toString(36).toUpperCase();
  const productSku = `UI-FEEDBACK-${suffix}`;
  const productTitle = `Producto sintético UI ${suffix}`;
  const existingFeedbackProduct = staffPage.locator('tr').filter({ hasText: 'Producto sintético UI' }).first();
  if (await existingFeedbackProduct.count()) {
    await existingFeedbackProduct.getByRole('button', { name: 'Editar' }).click();
    await productForm.locator('#product-title-input').fill(productTitle);
    const productUpdated = staffPage.waitForResponse((response) => response.request().method() === 'PUT'
      && new URL(response.url()).pathname.startsWith('/api/v1/catalog/products/'));
    await productForm.getByRole('button', { name: 'Guardar producto' }).click();
    assert.equal((await productUpdated).status(), 200, 'a product success toast must follow a backend-confirmed update');
    await staffPage.getByRole('status').filter({ hasText: 'El producto fue actualizado.' }).waitFor();
  } else {
    await productForm.locator('#product-sku').fill(productSku);
    await productForm.locator('#product-title-input').fill(productTitle);
    const productCreated = staffPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/products');
    await productForm.getByRole('button', { name: 'Registrar producto' }).click();
    assert.equal((await productCreated).status(), 201, 'a product success toast must follow a backend-confirmed creation');
    await staffPage.getByRole('status').filter({ hasText: 'El producto fue registrado en el catálogo del tenant.' }).waitFor();
  }
  await staffPage.getByText(productTitle, { exact: true }).waitFor();

  const stockForm = staffPage.locator('form.stock-form');
  await stockForm.locator('#stock-quantity').fill('0');
  await stockForm.locator('#stock-reason').fill('Validación sin persistencia');
  await stockForm.getByRole('button', { name: 'Registrar movimiento' }).click();
  await stockForm.getByText('Indica una cantidad entera distinta de cero.').waitFor();
  assert.equal(await stockForm.locator('#stock-quantity').getAttribute('aria-invalid'), 'true');
  await staffPage.getByRole('status').filter({ hasText: 'Completa producto, cantidad y motivo antes de registrar el movimiento.' }).waitFor();
  await staffPage.screenshot({ path: '__screenshots__/form-feedback-staff-invalid.png', fullPage: true });
  assert.equal((await browserFetch(staffPage, '/api/v1/session-context')).status, 200,
    'the staff session must remain active after invalid form interactions');
  await staffContext.close();

  process.stdout.write('FORM_FEEDBACK_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginClient(page) {
  await page.goto(`${baseUrl}/client/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo de cliente').fill(process.env.AV_DEMO_CLIENT_AURORA_EMAIL);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_CLIENT_AURORA_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${baseUrl}/client-space`);
}

async function loginStaff(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel('Contraseña').fill(process.env.AV_DEMO_AURORA_PASSWORD);
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
