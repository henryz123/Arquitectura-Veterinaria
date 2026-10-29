package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** Two committed POS confirmations must not sell the same final stock unit twice. */
@SpringBootTest
class PointOfSaleConcurrencyCommitIntegrationTest {

	@Autowired private CatalogService catalogService;
	@Autowired private StockService stockService;
	@Autowired private PointOfSaleService pointOfSaleService;
	@Autowired private IdentityAccessService identityAccessService;

	private UUID tenantId;
	private UUID productId;
	private UUID firstSaleId;
	private UUID secondSaleId;

	@Test
	void concurrentConfirmationsCompleteExactlyOneSaleWhenOneUnitExists() throws Exception {
		SessionPrincipal administrator = administrator();
		SessionPrincipal cashier = cashier(administrator);
		tenantId = administrator.session().tenantId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		CatalogProduct product = catalogService.createProduct(administrator,
				new CatalogProductDraft(null, "B04-POS-RACE-" + suffix, "Producto POS carrera " + suffix, null,
						new BigDecimal("9.00"), "ACTIVO_NO_PUBLICADO", true, null));
		productId = product.id();
		stockService.adjust(administrator, product.id(), new StockAdjustmentDraft(1, "Saldo POS carrera", UUID.randomUUID()));
		PosSale firstSale = pointOfSaleService.createSale(cashier,
				new PosSaleDraft(UUID.randomUUID(), List.of(new PosSaleLineDraft(product.id(), 1))));
		PosSale secondSale = pointOfSaleService.createSale(cashier,
				new PosSaleDraft(UUID.randomUUID(), List.of(new PosSaleLineDraft(product.id(), 1))));
		firstSaleId = firstSale.id();
		secondSaleId = secondSale.id();

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ConfirmationAttempt> first = executor.submit(() -> confirmWhenReleased(cashier, firstSale.id(), ready, start));
			Future<ConfirmationAttempt> second = executor.submit(() -> confirmWhenReleased(cashier, secondSale.id(), ready, start));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			List<ConfirmationAttempt> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
			assertEquals(1, outcomes.stream().filter(ConfirmationAttempt::succeeded).count());
			assertEquals(1, outcomes.stream().filter(item -> "B04_STOCK_INSUFFICIENT".equals(item.errorCode())).count());
		} finally {
			executor.shutdownNow();
		}

		assertEquals(0, stockService.listBalances(administrator).stream()
				.filter(balance -> balance.productId().equals(product.id())).findFirst().orElseThrow().availableQuantity());
		assertEquals(2, stockService.listMovements(administrator, product.id()).size());
		assertEquals(1, List.of(firstSale, secondSale).stream()
				.map(sale -> pointOfSaleService.findSale(cashier, sale.id()).status()).filter("COMPLETADA"::equals).count());
	}

	@AfterEach
	void removeCommittedFixture() throws SQLException {
		if (productId == null) return;
		try (Connection connection = DriverManager.getConnection(required("AV_DB_URL"), required("AV_MIGRATION_DB_USER"), required("AV_MIGRATION_DB_PASSWORD"))) {
			connection.setAutoCommit(false);
			try (PreparedStatement movements = connection.prepareStatement("delete from inventory_sales.stock_movement where tenant_id = ? and product_id = ?");
					PreparedStatement payments = connection.prepareStatement("delete from inventory_sales.pos_manual_payment where tenant_id = ? and sale_id = ?");
					PreparedStatement lines = connection.prepareStatement("delete from inventory_sales.pos_sale_line where tenant_id = ? and sale_id = ?");
					PreparedStatement sales = connection.prepareStatement("delete from inventory_sales.pos_sale where tenant_id = ? and id = ?");
					PreparedStatement balance = connection.prepareStatement("delete from inventory_sales.stock_balance where tenant_id = ? and product_id = ?");
					PreparedStatement product = connection.prepareStatement("delete from inventory_sales.product where tenant_id = ? and id = ?")) {
				movements.setObject(1, tenantId); movements.setObject(2, productId); movements.executeUpdate();
				for (UUID saleId : new UUID[] { firstSaleId, secondSaleId }) {
					if (saleId == null) continue;
					payments.setObject(1, tenantId); payments.setObject(2, saleId); payments.executeUpdate();
					lines.setObject(1, tenantId); lines.setObject(2, saleId); lines.executeUpdate();
					sales.setObject(1, tenantId); sales.setObject(2, saleId); sales.executeUpdate();
				}
				balance.setObject(1, tenantId); balance.setObject(2, productId); balance.executeUpdate();
				product.setObject(1, tenantId); product.setObject(2, productId); product.executeUpdate();
			}
			connection.commit();
		} finally { productId = null; }
	}

	private ConfirmationAttempt confirmWhenReleased(SessionPrincipal cashier, UUID saleId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		assertTrue(start.await(5, TimeUnit.SECONDS));
		try {
			pointOfSaleService.confirmSale(cashier, saleId, new PosSaleConfirmationDraft("EFECTIVO", new BigDecimal("9.00")));
			return new ConfirmationAttempt(true, null);
		} catch (ApiException exception) {
			return new ConfirmationAttempt(false, exception.code());
		}
	}

	private SessionPrincipal administrator() {
		String password = required("AV_DEMO_AURORA_PASSWORD");
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal cashier(SessionPrincipal source) {
		TenantSession session = source.session();
		return new SessionPrincipal(new TenantSession(session.sessionId(), session.accountId(), session.tenantId(), session.tenantSlug(),
				session.tenantCommercialName(), session.tenantStatus(), session.accountType(), List.of("CAJERO"),
				session.sessionTokenHash(), Instant.now().plusSeconds(3600)));
	}

	private String required(String name) {
		String value = System.getenv(name);
		assertNotNull(value, "La regresion requiere la variable local " + name + ".");
		return value;
	}

	private record ConfirmationAttempt(boolean succeeded, String errorCode) { }
}
