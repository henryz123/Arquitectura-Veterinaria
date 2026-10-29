import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const required = ['AV_DEMO_AURORA_PASSWORD', 'AV_DEMO_BOSQUE_PASSWORD', 'AV_E2E_CHROME_PATH'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing required local E2E environment variable: ${key}`);
}

const baseUrl = (process.env.AV_E2E_BASE_URL ?? 'http://127.0.0.1:4200').replace(/\/$/, '');
const staffEmail = process.env.AV_E2E_AURORA_EMAIL ?? 'administracion@demo.invalid';
const sku = 'B04-E2E-STOCK-001';
const title = 'Producto sintético stock B04';
const targetQuantity = 5;
const browser = await chromium.launch({ executablePath: process.env.AV_E2E_CHROME_PATH, headless: true });

try {
  const aurora = await browser.newContext();
  const page = await aurora.newPage();
  await loginStaff(page, 'veterinaria-aurora', staffEmail, process.env.AV_DEMO_AURORA_PASSWORD);
  await page.goto(`${baseUrl}/inventory`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Stock y movimientos' }).waitFor();

  let products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  let product = products.find((item) => item.sku === sku);
  if (!product) {
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/catalog/products');
    await page.locator('#product-sku').fill(sku);
    await page.locator('#product-title-input').fill(title);
    await page.locator('#product-price').fill('21.00');
    await page.locator('#product-status').selectOption('ACTIVO_NO_PUBLICADO');
    await page.locator('#inventory-managed').check();
    await page.getByRole('button', { name: 'Registrar producto' }).click();
    assert.equal((await created).status(), 201, 'staff must create the synthetic inventory product through Angular');
  }
  products = (await browserFetch(page, '/api/v1/catalog/products')).body?.data ?? [];
  product = products.find((item) => item.sku === sku);
  assert.equal(product?.inventoryManaged, true, 'the stock fixture must be inventory managed');
  assert.ok(product?.id, 'the stock fixture must persist in Aurora');

  const balances = (await browserFetch(page, '/api/v1/inventory/stock')).body?.data ?? [];
  const existing = balances.find((item) => item.productId === product.id);
  const delta = targetQuantity - (existing?.availableQuantity ?? 0);
  if (delta !== 0) {
    await page.locator('#stock-product option', { hasText: sku }).waitFor({ state: 'attached' });
    await page.locator('#stock-product').selectOption(product.id);
    await page.locator('#stock-quantity').fill(String(delta));
    await page.locator('#stock-reason').fill('Saldo sintético controlado B04');
    const adjusted = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/inventory/stock/products/${product.id}/adjustments`));
    await page.getByRole('button', { name: 'Registrar movimiento' }).click();
    assert.equal((await adjusted).status(), 201, 'the visible stock form must persist its adjustment');
  }

  await page.reload({ waitUntil: 'networkidle' });
  const reloadedBalances = (await browserFetch(page, '/api/v1/inventory/stock')).body?.data ?? [];
  const balance = reloadedBalances.find((item) => item.productId === product.id);
  assert.equal(balance?.availableQuantity, targetQuantity, 'the persisted stock balance must survive Angular reload');
  const movements = (await browserFetch(page, `/api/v1/inventory/stock/movements?productId=${product.id}`)).body?.data ?? [];
  assert.ok(movements.length >= 1, 'the adjustment must produce at least one immutable movement');
  assert.equal(movements.every((item) => item.productId === product.id), true, 'the movement filter must remain product-local');
  await aurora.close();

  const bosque = await browser.newContext();
  const bosquePage = await bosque.newPage();
  await loginStaff(bosquePage, 'veterinaria-bosque', staffEmail, process.env.AV_DEMO_BOSQUE_PASSWORD);
  const bosqueBalances = (await browserFetch(bosquePage, '/api/v1/inventory/stock')).body?.data ?? [];
  assert.equal(bosqueBalances.some((item) => item.productId === product.id), false,
    'Bosque must not receive Aurora stock balances through the protected API');
  const bosqueMovements = (await browserFetch(bosquePage, `/api/v1/inventory/stock/movements?productId=${product.id}`)).body?.data ?? [];
  assert.equal(bosqueMovements.length, 0, 'Bosque must not receive Aurora stock movements through the protected API');
  await bosque.close();
  process.stdout.write('B04_STOCK_BROWSER_E2E=PASS\n');
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
