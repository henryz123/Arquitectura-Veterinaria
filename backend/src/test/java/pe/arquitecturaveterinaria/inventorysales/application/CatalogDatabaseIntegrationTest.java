package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * Uses only the local synthetic database. The class transaction rolls back
 * categories and products after each case; it never enables Flyway.
 */
@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class CatalogDatabaseIntegrationTest {

	@Autowired
	private CatalogService catalogService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Test
	void persistsTenantLocalCategoriesAndProductsWithoutAcceptingCrossTenantIds() {
		SessionPrincipal aurora = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal bosque = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		CatalogCategory category = catalogService.createCategory(aurora,
				new CatalogCategoryDraft("B04 Alimentos " + suffix, "ACTIVA"));
		assertNotNull(category.id());
		assertTrue(catalogService.listCategories(aurora, suffix, "ACTIVA").stream()
				.anyMatch(item -> item.id().equals(category.id())));

		CatalogProduct product = catalogService.createProduct(aurora,
				new CatalogProductDraft(category.id(), "B04-" + suffix, "Alimento sintético " + suffix,
						"Producto de integración", new BigDecimal("52.50"), "ACTIVO_NO_PUBLICADO", true, null));
		assertEquals(category.name(), product.categoryName());
		assertEquals(new BigDecimal("52.50"), product.salePricePen());
		assertTrue(catalogService.listProducts(aurora, suffix, "ACTIVO_NO_PUBLICADO", category.id()).stream()
				.anyMatch(item -> item.id().equals(product.id())));

		CatalogProduct updated = catalogService.updateProduct(aurora, product.id(),
				new CatalogProductDraft(category.id(), product.sku(), "Alimento actualizado " + suffix,
						"Actualización autorizada", new BigDecimal("53.00"), "ACTIVO_PUBLICADO", true, null));
		assertEquals("ACTIVO_PUBLICADO", updated.status());
		assertEquals(new BigDecimal("53.00"), updated.salePricePen());

		ApiException duplicate = assertThrows(ApiException.class, () -> catalogService.createProduct(aurora,
				new CatalogProductDraft(category.id(), product.sku(), "SKU repetido", null, new BigDecimal("1.00"),
						"ACTIVO_NO_PUBLICADO", true, null)));
		assertEquals(HttpStatus.CONFLICT, duplicate.status());
		assertEquals("B04_PRODUCT_DUPLICATE", duplicate.code());

		CatalogProduct secondProduct = catalogService.createProduct(aurora,
				new CatalogProductDraft(category.id(), "B04-SECOND-" + suffix, "Segundo producto " + suffix,
						null, new BigDecimal("2.00"), "ACTIVO_NO_PUBLICADO", true, null));
		ApiException duplicateUpdate = assertThrows(ApiException.class, () -> catalogService.updateProduct(aurora,
				secondProduct.id(), new CatalogProductDraft(category.id(), product.sku(), secondProduct.title(), null,
						new BigDecimal("2.00"), "ACTIVO_NO_PUBLICADO", true, null)));
		assertEquals(HttpStatus.CONFLICT, duplicateUpdate.status());
		assertEquals("B04_PRODUCT_DUPLICATE", duplicateUpdate.code());
		assertTrue(catalogService.listProducts(aurora, "SECOND-" + suffix, null, null).stream()
				.anyMatch(item -> item.id().equals(secondProduct.id())));

		ApiException crossTenant = assertThrows(ApiException.class, () -> catalogService.updateProduct(bosque, product.id(),
				new CatalogProductDraft(null, "BOSQUE-" + suffix, "Intento aislado", null, new BigDecimal("1.00"),
						"ACTIVO_NO_PUBLICADO", true, null)));
		assertEquals(HttpStatus.NOT_FOUND, crossTenant.status());
		assertEquals("B04_CATALOG_NOT_FOUND", crossTenant.code());

		ApiException invalidCategory = assertThrows(ApiException.class, () -> catalogService.createProduct(aurora,
				new CatalogProductDraft(UUID.randomUUID(), "B04-X" + suffix, "Categoría inválida", null,
						new BigDecimal("1.00"), "ACTIVO_NO_PUBLICADO", true, null)));
		assertEquals(HttpStatus.NOT_FOUND, invalidCategory.status());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintéticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
	}
}
