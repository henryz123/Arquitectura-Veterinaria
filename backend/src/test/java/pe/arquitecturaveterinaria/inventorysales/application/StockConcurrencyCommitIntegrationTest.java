package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * Uses two independently committed service transactions. A shared start gate
 * makes both attempt to consume the same unit; the row lock and non-negative
 * balance invariant must allow exactly one reduction.
 */
@SpringBootTest
class StockConcurrencyCommitIntegrationTest {

	@Autowired
	private CatalogService catalogService;

	@Autowired
	private StockService stockService;

	@Autowired
	private IdentityAccessService identityAccessService;

	private UUID fixtureTenantId;
	private UUID fixtureProductId;

	@Test
	void concurrentReductionsCommitAtMostOneMovementWhenOnlyOneUnitIsAvailable() throws Exception {
		SessionPrincipal administrator = administrator();
		fixtureTenantId = administrator.session().tenantId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		CatalogProduct product = catalogService.createProduct(administrator,
				new CatalogProductDraft(null, "B04-RACE-" + suffix, "Producto carrera " + suffix, null,
						new BigDecimal("10.00"), "ACTIVO_NO_PUBLICADO", true, null));
		fixtureProductId = product.id();
		stockService.adjust(administrator, product.id(), new StockAdjustmentDraft(1, "Saldo para carrera", UUID.randomUUID()));

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ReductionAttempt> first = executor.submit(() -> reduceWhenReleased(administrator, product.id(), ready, start));
			Future<ReductionAttempt> second = executor.submit(() -> reduceWhenReleased(administrator, product.id(), ready, start));
			assertTrue(ready.await(5, TimeUnit.SECONDS), "Las dos transacciones deben estar listas antes de competir.");
			start.countDown();

			List<ReductionAttempt> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
			assertEquals(1, outcomes.stream().filter(ReductionAttempt::succeeded).count());
			assertEquals(1, outcomes.stream().filter(item -> "B04_STOCK_INSUFFICIENT".equals(item.errorCode())).count());
		} finally {
			executor.shutdownNow();
		}

		StockBalance balance = stockService.listBalances(administrator).stream()
				.filter(item -> item.productId().equals(product.id())).findFirst().orElseThrow();
		assertEquals(0, balance.availableQuantity());
		assertEquals(2, stockService.listMovements(administrator, product.id()).size());
	}

	@AfterEach
	void removeCommittedFixture() throws SQLException {
		if (fixtureProductId == null) {
			return;
		}
		try (Connection connection = DriverManager.getConnection(requiredEnvironment("AV_DB_URL"),
				requiredEnvironment("AV_MIGRATION_DB_USER"), requiredEnvironment("AV_MIGRATION_DB_PASSWORD"))) {
			connection.setAutoCommit(false);
			try (PreparedStatement deleteMovements = connection.prepareStatement(
					"delete from inventory_sales.stock_movement where tenant_id = ? and product_id = ?");
					PreparedStatement deleteBalance = connection.prepareStatement(
							"delete from inventory_sales.stock_balance where tenant_id = ? and product_id = ?");
					PreparedStatement deleteProduct = connection.prepareStatement(
							"delete from inventory_sales.product where tenant_id = ? and id = ?")) {
				for (PreparedStatement statement : List.of(deleteMovements, deleteBalance, deleteProduct)) {
					statement.setObject(1, fixtureTenantId);
					statement.setObject(2, fixtureProductId);
					statement.executeUpdate();
				}
			}
			assertEquals(0, remainingFixtures(connection));
			connection.commit();
		} finally {
			fixtureProductId = null;
		}
	}

	private ReductionAttempt reduceWhenReleased(SessionPrincipal administrator, UUID productId, CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		assertTrue(start.await(5, TimeUnit.SECONDS), "La barrera de concurrencia no fue liberada.");
		try {
			stockService.adjust(administrator, productId, new StockAdjustmentDraft(-1, "Descuento concurrente", UUID.randomUUID()));
			return new ReductionAttempt(true, null);
		} catch (ApiException exception) {
			return new ReductionAttempt(false, exception.code());
		}
	}

	private int remainingFixtures(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"select count(*) from inventory_sales.product where tenant_id = ? and id = ?")) {
			statement.setObject(1, fixtureTenantId);
			statement.setObject(2, fixtureProductId);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private SessionPrincipal administrator() {
		String password = requiredEnvironment("AV_DEMO_AURORA_PASSWORD");
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid", password).context());
	}

	private String requiredEnvironment(String name) {
		String value = System.getenv(name);
		assertNotNull(value, "La regresion requiere la variable local " + name + ".");
		return value;
	}

	private record ReductionAttempt(boolean succeeded, String errorCode) {
	}
}
