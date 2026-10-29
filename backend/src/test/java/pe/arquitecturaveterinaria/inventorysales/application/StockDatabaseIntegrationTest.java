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

/** Integration proof for V024 balance, movement, retry and tenant invariants. */
@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class StockDatabaseIntegrationTest {

	@Autowired
	private CatalogService catalogService;

	@Autowired
	private StockService stockService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Test
	void appliesOneImmutableMovementPerOperationAndNeverAllowsNegativeOrCrossTenantStock() {
		SessionPrincipal aurora = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal bosque = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		CatalogProduct product = catalogService.createProduct(aurora,
				new CatalogProductDraft(null, "B04-STOCK-" + suffix, "Producto stock " + suffix, null,
						new BigDecimal("10.00"), "ACTIVO_NO_PUBLICADO", true, null));
		UUID initialOperation = UUID.randomUUID();

		StockAdjustment initial = stockService.adjust(aurora, product.id(),
				new StockAdjustmentDraft(5, "Saldo inicial sintético", initialOperation));
		assertEquals("ALTA_INICIAL", initial.movement().movementType());
		assertEquals(5, initial.balance().availableQuantity());
		StockAdjustment replay = stockService.adjust(aurora, product.id(),
				new StockAdjustmentDraft(5, "Saldo inicial sintético", initialOperation));
		assertEquals(initial.movement().id(), replay.movement().id());
		assertEquals(1, stockService.listMovements(aurora, product.id()).size());

		StockAdjustment reduction = stockService.adjust(aurora, product.id(),
				new StockAdjustmentDraft(-2, "Ajuste físico sintético", UUID.randomUUID()));
		assertEquals("AJUSTE_NEGATIVO", reduction.movement().movementType());
		assertEquals(3, reduction.balance().availableQuantity());
		assertEquals(2, stockService.listMovements(aurora, product.id()).size());

		ApiException insufficient = assertThrows(ApiException.class, () -> stockService.adjust(aurora, product.id(),
				new StockAdjustmentDraft(-4, "No debe permitir negativo", UUID.randomUUID())));
		assertEquals(HttpStatus.CONFLICT, insufficient.status());
		assertEquals("B04_STOCK_INSUFFICIENT", insufficient.code());
		assertEquals(3, stockService.listBalances(aurora).stream()
				.filter(balance -> balance.productId().equals(product.id())).findFirst().orElseThrow().availableQuantity());

		ApiException crossTenant = assertThrows(ApiException.class, () -> stockService.adjust(bosque, product.id(),
				new StockAdjustmentDraft(1, "Intento tenant ajeno", UUID.randomUUID())));
		assertEquals(HttpStatus.NOT_FOUND, crossTenant.status());
		assertEquals("B04_STOCK_NOT_FOUND", crossTenant.code());

		CatalogProduct nonInventoriable = catalogService.createProduct(aurora,
				new CatalogProductDraft(null, "B04-SERVICE-" + suffix, "Servicio sin stock " + suffix, null,
						new BigDecimal("0.00"), "ACTIVO_NO_PUBLICADO", false, null));
		ApiException serviceRejected = assertThrows(ApiException.class, () -> stockService.adjust(aurora, nonInventoriable.id(),
				new StockAdjustmentDraft(1, "No administra unidades", UUID.randomUUID())));
		assertEquals(HttpStatus.CONFLICT, serviceRejected.status());
		assertEquals("B04_PRODUCT_NOT_INVENTORIABLE", serviceRejected.code());
		assertTrue(stockService.listMovements(aurora, nonInventoriable.id()).isEmpty());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintéticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
	}
}
