import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_E2E_CHROME_PATH'];
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
const fixture = {
  categoryName: 'Higiene y cuidado',
  sku: 'DEMO-CHAMPU-250',
  title: 'Champú Canino Suave 250 ml',
  price: '18.50',
  targetQuantity: 15,
  reason: 'Ingreso inicial para demostración académica',
};
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const context = await browser.newContext();
  const page = await context.newPage();
  await loginStaff(page);
  await page.getByRole('link', { name: 'Productos e inventario' }).click();
  await page.waitForURL(`${baseUrl}/inventory`);
  await page.getByRole('heading', { name: 'Productos de la veterinaria' }).waitFor();

  let categories = (await browserFetch(page, '/api/v1/catalog/categories')).body?.data ?? [];
  let category = categories.find((item) => item.name === fixture.categoryName);
  if (!category) {
    await page.locator('#category-name').fill(fixture.categoryName);
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/categories');
    await page.getByRole('button', { name: 'Registrar categoría' }).click();
    assert.equal((await created).status(), 201, 'the category form must persist the demonstration category');
    categories = (await browserFetch(page, '/api/v1/catalog/categories')).body?.data ?? [];
    category = categories.find((item) => item.name === fixture.categoryName);
  }
  assert.ok(category?.id, 'the demonstration category must be available for the product form');

  let products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  let product = products.find((item) => item.sku === fixture.sku);
  if (!product) {
    await page.locator('#product-sku').fill(fixture.sku);
    await page.locator('#product-title-input').fill(fixture.title);
    await page.locator('#product-category').selectOption(category.id);
    await page.locator('#product-price').fill(fixture.price);
    await page.locator('#product-status').selectOption('ACTIVO_NO_PUBLICADO');
    await page.locator('#inventory-managed').check();
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/products');
    await page.getByRole('button', { name: 'Registrar producto' }).click();
    assert.equal((await created).status(), 201, 'the product form must persist the demonstration shampoo');
    products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
    product = products.find((item) => item.sku === fixture.sku);
  }
  assert.equal(product?.title, fixture.title, 'the catalogue must return the presentation product from PostgreSQL');
  assert.equal(product?.categoryId, category.id, 'the presentation product must retain its selected category');
  assert.equal(product?.inventoryManaged, true, 'the presentation product must use the shared inventory balance');

  const balances = (await browserFetch(page, '/api/v1/inventory/stock')).body?.data ?? [];
  const currentBalance = balances.find((item) => item.productId === product.id);
  const delta = fixture.targetQuantity - (currentBalance?.availableQuantity ?? 0);
  if (delta !== 0) {
    await page.locator('#stock-product').selectOption(product.id);
    await page.locator('#stock-quantity').fill(String(delta));
    await page.locator('#stock-reason').fill(fixture.reason);
    const adjusted = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/inventory/stock/products/${product.id}/adjustments`));
    await page.getByRole('button', { name: 'Registrar movimiento' }).click();
    assert.equal((await adjusted).status(), 201, 'the stock form must record the real initial demonstration movement');
  }

  await page.reload({ waitUntil: 'networkidle' });
  const reloadedProducts = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  const reloadedProduct = reloadedProducts.find((item) => item.sku === fixture.sku);
  const reloadedBalances = (await browserFetch(page, '/api/v1/inventory/stock')).body?.data ?? [];
  const reloadedBalance = reloadedBalances.find((item) => item.productId === product.id);
  const movements = (await browserFetch(page, `/api/v1/inventory/stock/movements?productId=${product.id}`)).body?.data ?? [];
  assert.equal(reloadedProduct?.title, fixture.title, 'the product must remain visible after a browser reload');
  assert.equal(reloadedBalance?.availableQuantity, fixture.targetQuantity, 'the real stock balance must persist after a browser reload');
  assert.equal(movements.some((movement) => movement.productId === product.id), true,
    'the stock page must expose at least one immutable movement for the presentation product');
  await page.getByText(fixture.title, { exact: true }).first().waitFor();
  if (screenshotPath) {
    await mkdir(dirname(screenshotPath), { recursive: true });
    await page.screenshot({ path: screenshotPath, fullPage: true });
  }
  await context.close();

  process.stdout.write('DEMO_INVENTORY_BROWSER_E2E=PASS\n');
} finally {
  await browser.close();
}

async function loginStaff(page) {
  await page.goto(`${baseUrl}/login/veterinaria-aurora`, { waitUntil: 'networkidle' });
  await page.getByLabel('Correo institucional').fill(staffEmail);
  await page.getByLabel(/Contrase/).fill(process.env.AV_DEMO_AURORA_PASSWORD);
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
