package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** Integration proof for V025 draft, receipt, payment and the single V024 stock authority. */
@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class PointOfSaleDatabaseIntegrationTest {

	@Autowired
	private CatalogService catalogService;

	@Autowired
	private StockService stockService;

	@Autowired
	private PointOfSaleService pointOfSaleService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Test
	void completesOneReceiptExactlyOnceAndDoesNotMoveStockForServices() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal cashier = cashier(administrator);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		CatalogProduct product = catalogService.createProduct(administrator,
				new CatalogProductDraft(null, "B04-POS-" + suffix, "Producto POS " + suffix, null,
						new BigDecimal("7.50"), "ACTIVO_NO_PUBLICADO", true, null));
		CatalogProduct service = catalogService.createProduct(administrator,
				new CatalogProductDraft(null, "B04-POS-SVC-" + suffix, "Servicio POS " + suffix, null,
						new BigDecimal("10.00"), "ACTIVO_NO_PUBLICADO", false, null));
		stockService.adjust(administrator, product.id(), new StockAdjustmentDraft(2, "Saldo POS sintetico", UUID.randomUUID()));

		UUID operationId = UUID.randomUUID();
		PosSale sale = pointOfSaleService.createSale(cashier, new PosSaleDraft(operationId,
				List.of(new PosSaleLineDraft(product.id(), 2), new PosSaleLineDraft(service.id(), 1))));
		assertEquals("PREPARACION", sale.status());
		assertEquals(new BigDecimal("25.00"), sale.totalAmountPen());
		assertNull(sale.payment());
		assertEquals(2, sale.lines().size());
		assertEquals(sale.id(), pointOfSaleService.createSale(cashier, new PosSaleDraft(operationId,
				List.of(new PosSaleLineDraft(service.id(), 1), new PosSaleLineDraft(product.id(), 2)))).id());

		PosSale completed = pointOfSaleService.confirmSale(cashier, sale.id(),
				new PosSaleConfirmationDraft("EFECTIVO", new BigDecimal("25.00")));
		assertEquals("COMPLETADA", completed.status());
		assertNotNull(completed.completedAt());
		assertNotNull(completed.payment());
		assertEquals("VALIDADO", completed.payment().status());
		assertEquals(new BigDecimal("25.00"), completed.payment().declaredAmountPen());
		assertEquals(0, stockService.listBalances(administrator).stream()
				.filter(balance -> balance.productId().equals(product.id())).findFirst().orElseThrow().availableQuantity());
		assertEquals(2, stockService.listMovements(administrator, product.id()).size());

		PosSale confirmationReplay = pointOfSaleService.confirmSale(cashier, sale.id(),
				new PosSaleConfirmationDraft("TRANSFERENCIA", new BigDecimal("25.00")));
		assertEquals(completed.id(), confirmationReplay.id());
		assertEquals(2, stockService.listMovements(administrator, product.id()).size());
	}

	@Test
	void rejectsInactiveInsufficientAndCrossTenantSalesWithoutPartialStockMutation() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal auroraCashier = cashier(auroraAdministrator);
		SessionPrincipal bosqueCashier = cashier(administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		CatalogProduct product = catalogService.createProduct(auroraAdministrator,
				new CatalogProductDraft(null, "B04-POS-LOW-" + suffix, "Producto limitado " + suffix, null,
						new BigDecimal("3.00"), "ACTIVO_NO_PUBLICADO", true, null));
		stockService.adjust(auroraAdministrator, product.id(), new StockAdjustmentDraft(1, "Saldo bajo POS", UUID.randomUUID()));
		PosSale insufficientSale = pointOfSaleService.createSale(auroraCashier, new PosSaleDraft(UUID.randomUUID(),
				List.of(new PosSaleLineDraft(product.id(), 2))));
		ApiException insufficient = assertThrows(ApiException.class, () -> pointOfSaleService.confirmSale(auroraCashier,
				insufficientSale.id(), new PosSaleConfirmationDraft("EFECTIVO", new BigDecimal("6.00"))));
		assertEquals(HttpStatus.CONFLICT, insufficient.status());
		assertEquals("B04_STOCK_INSUFFICIENT", insufficient.code());
		assertEquals("PREPARACION", pointOfSaleService.findSale(auroraCashier, insufficientSale.id()).status());
		assertEquals(1, stockService.listBalances(auroraAdministrator).stream()
				.filter(balance -> balance.productId().equals(product.id())).findFirst().orElseThrow().availableQuantity());
		assertEquals(1, stockService.listMovements(auroraAdministrator, product.id()).size());

		ApiException crossTenant = assertThrows(ApiException.class, () -> pointOfSaleService.createSale(bosqueCashier,
				new PosSaleDraft(UUID.randomUUID(), List.of(new PosSaleLineDraft(product.id(), 1)))));
		assertEquals(HttpStatus.NOT_FOUND, crossTenant.status());
		assertEquals("B04_SALE_NOT_FOUND", crossTenant.code());

		CatalogProduct inactive = catalogService.updateProduct(auroraAdministrator, product.id(),
				new CatalogProductDraft(null, product.sku(), product.title(), product.description(), product.salePricePen(), "INACTIVO",
						product.inventoryManaged(), product.imageUrl()));
		assertEquals("INACTIVO", inactive.status());
		ApiException inactiveRejected = assertThrows(ApiException.class, () -> pointOfSaleService.createSale(auroraCashier,
				new PosSaleDraft(UUID.randomUUID(), List.of(new PosSaleLineDraft(product.id(), 1)))));
		assertEquals("B04_PRODUCT_INACTIVE", inactiveRejected.code());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sinteticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal cashier(SessionPrincipal source) {
		TenantSession session = source.session();
		return new SessionPrincipal(new TenantSession(session.sessionId(), session.accountId(), session.tenantId(), session.tenantSlug(),
				session.tenantCommercialName(), session.tenantStatus(), session.accountType(), List.of("CAJERO"),
				session.sessionTokenHash(), Instant.now().plusSeconds(3600)));
	}
}
