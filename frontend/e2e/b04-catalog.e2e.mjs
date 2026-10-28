import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_BOSQUE_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const categoryName = 'B04 E2E Catalogo';
const sku = 'B04-E2E-CAT-001';
const initialTitle = 'Producto sintetico B04';
const updatedTitle = 'Producto sintetico B04 actualizado';
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const aurora = await browser.newContext();
  const page = await aurora.newPage();
  await loginStaff(page, 'veterinaria-aurora', staffEmail, process.env.AV_DEMO_AURORA_PASSWORD);
  await page.goto(`${baseUrl}/inventory`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Productos de la veterinaria' }).waitFor();

  let categories = (await browserFetch(page, '/api/v1/catalog/categories')).body?.data ?? [];
  let category = categories.find((item) => item.name === categoryName);
  if (!category) {
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/categories');
    await page.locator('#category-name').fill(categoryName);
    await page.locator('#category-status').selectOption('ACTIVA');
    await page.getByRole('button', { name: 'Registrar categoría' }).click();
    assert.equal((await created).status(), 201, 'staff must create a category through the visible catalog form');
  }
  await page.locator('#product-category option', { hasText: categoryName }).waitFor({ state: 'attached' });
  categories = (await browserFetch(page, '/api/v1/catalog/categories')).body?.data ?? [];
  category = categories.find((item) => item.name === categoryName);
  assert.ok(category?.id, 'the E2E category must persist in Aurora');
  if (category.status !== 'ACTIVA') {
    const categoryRow = page.locator(`[data-category-id="${category.id}"]`);
    await categoryRow.getByRole('button', { name: 'Editar' }).click();
    await page.locator('#category-status').selectOption('ACTIVA');
    const updated = page.waitForResponse((response) => response.request().method() === 'PUT'
      && new URL(response.url()).pathname.endsWith(`/catalog/categories/${category.id}`));
    await page.getByRole('button', { name: 'Guardar categoría' }).click();
    assert.equal((await updated).status(), 200, 'staff must reactivate the synthetic category through the visible form');
  }

  let products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  let product = products.find((item) => item.sku === sku);
  if (!product) {
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/products');
    await page.locator('#product-sku').fill(sku);
    await page.locator('#product-title-input').fill(initialTitle);
    await page.locator('#product-category').selectOption(category.id);
    await page.locator('#product-price').fill('52.50');
    await page.locator('#product-status').selectOption('ACTIVO_NO_PUBLICADO');
    await page.getByRole('button', { name: 'Registrar producto' }).click();
    assert.equal((await created).status(), 201, 'staff must persist a product through the visible catalog form');
  }
  await page.waitForTimeout(150);
  products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  product = products.find((item) => item.sku === sku);
  assert.equal(product?.categoryId, category.id, 'the product must retain its tenant-local category');

  const productRow = page.locator(`[data-product-id="${product.id}"]`);
  await productRow.waitFor();
  await productRow.getByRole('button', { name: 'Editar' }).click();
  await page.locator('#product-title-input').fill(updatedTitle);
  await page.locator('#product-price').fill('53.00');
  await page.locator('#product-status').selectOption('ACTIVO_PUBLICADO');
  const updated = page.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname.endsWith(`/catalog/products/${product.id}`));
  await page.getByRole('button', { name: 'Guardar producto' }).click();
  assert.equal((await updated).status(), 200, 'staff must update the product through the visible form');

  await page.reload({ waitUntil: 'networkidle' });
  const reloaded = (await browserFetch(page, '/api/v1/catalog/products')).body?.data
    ?.find((item) => item.id === product.id);
  assert.equal(reloaded?.title, updatedTitle, 'the Angular reload must show the persisted product update');
  assert.equal(reloaded?.salePricePen, 53, 'the persisted PEN price must remain decimal-exact in the API response');
  assert.equal(reloaded?.status, 'ACTIVO_PUBLICADO', 'the persisted product state must remain visible after reload');
  await aurora.close();

  const bosque = await browser.newContext();
  const bosquePage = await bosque.newPage();
  await loginStaff(bosquePage, 'veterinaria-bosque', staffEmail, process.env.AV_DEMO_BOSQUE_PASSWORD);
  await bosquePage.goto(`${baseUrl}/inventory`, { waitUntil: 'networkidle' });
  const bosqueProducts = (await browserFetch(bosquePage, '/api/v1/catalog/products')).body?.data ?? [];
  assert.equal(bosqueProducts.some((item) => item.id === product.id || item.sku === sku), false,
    'Bosque must not receive Aurora catalog records through the protected API');
  await bosque.close();
  process.stdout.write('B04_CATALOG_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page, tenantSlug, email, password) {
  await page.goto(`${baseUrl}/login/${tenantSlug}`, { waitUntil: 'networkidle' });
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
