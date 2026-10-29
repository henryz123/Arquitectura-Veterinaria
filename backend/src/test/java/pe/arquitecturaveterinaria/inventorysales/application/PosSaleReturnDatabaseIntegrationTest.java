package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** B04 return regression: no stock is restored before inspection and every restoration is single-shot. */
@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class PosSaleReturnDatabaseIntegrationTest {

	@Autowired private CatalogService catalogService;
	@Autowired private StockService stockService;
	@Autowired private PointOfSaleService pointOfSaleService;
	@Autowired private IdentityAccessService identityAccessService;

	@Test
	void cancelsAnUndispatchedSaleOnceAndRestoresOnlyItsPhysicalStock() {
		SessionPrincipal administrator = administrator();
		SessionPrincipal cashier = cashier(administrator);
		CatalogProduct product = inventoryProduct(administrator, "B04-CANCEL-");
		stockService.adjust(administrator, product.id(), new StockAdjustmentDraft(2, "Saldo para anulación", UUID.randomUUID()));
		PosSale sale = completedSale(cashier, product.id(), 2, new BigDecimal("10.00"));

		PosSaleCancellation cancellation = pointOfSaleService.cancelSale(administrator, sale.id(),
				new PosSaleCancellationDraft(UUID.randomUUID(), "La unidad no salió del establecimiento", false));
		assertEquals(sale.id(), cancellation.saleId());
		assertEquals(true, cancellation.stockRestocked());
		assertEquals("ANULADA", pointOfSaleService.findSale(cashier, sale.id()).status());
		assertEquals(2, available(administrator, product.id()));
		assertEquals(3, stockService.listMovements(administrator, product.id()).size());

		PosSaleCancellation replay = pointOfSaleService.cancelSale(administrator, sale.id(),
				new PosSaleCancellationDraft(cancellation.operationId(), "La unidad no salió del establecimiento", false));
		assertEquals(cancellation.id(), replay.id());
		assertEquals(3, stockService.listMovements(administrator, product.id()).size());
	}

	@Test
	void inspectsPhysicalReturnBeforeRestockingAndNeverExceedsSoldQuantity() {
		SessionPrincipal administrator = administrator();
		SessionPrincipal cashier = cashier(administrator);
		CatalogProduct product = inventoryProduct(administrator, "B04-RETURN-");
		stockService.adjust(administrator, product.id(), new StockAdjustmentDraft(3, "Saldo para devolución", UUID.randomUUID()));
		PosSale sale = completedSale(cashier, product.id(), 2, new BigDecimal("10.00"));
		PosSaleLine saleLine = sale.lines().getFirst();

		PosReturn posReturn = pointOfSaleService.requestReturn(cashier, sale.id(),
				new PosReturnDraft(UUID.randomUUID(), "Retorno físico para inspección",
						List.of(new PosReturnLineDraft(saleLine.id(), 2))));
		assertEquals("SOLICITADA", posReturn.status());
		assertEquals(1, available(administrator, product.id()));

		PosReturn received = pointOfSaleService.receiveReturn(cashier, posReturn.id(),
				List.of(new PosReturnReceiptLineDraft(posReturn.lines().getFirst().id(), 2)));
		assertEquals("RECIBIDA", received.status());
		assertEquals(1, available(administrator, product.id()));

		PosReturn inspected = pointOfSaleService.inspectReturn(administrator, posReturn.id(),
				List.of(new PosReturnInspectionLineDraft(received.lines().getFirst().id(), 1)));
		assertEquals("INSPECCIONADA_APTA", inspected.status());
		assertEquals("PARCIAL", inspected.lines().getFirst().inspectionResult());
		assertEquals(2, available(administrator, product.id()));
		assertEquals(3, stockService.listMovements(administrator, product.id()).size());

		PosReturn inspectionReplay = pointOfSaleService.inspectReturn(administrator, posReturn.id(),
				List.of(new PosReturnInspectionLineDraft(received.lines().getFirst().id(), 1)));
		assertEquals(inspected.id(), inspectionReplay.id());
		assertEquals(3, stockService.listMovements(administrator, product.id()).size());

		PosReturn closed = pointOfSaleService.closeReturn(cashier, posReturn.id());
		assertEquals("CERRADA", closed.status());
		assertEquals("DEVUELTA", pointOfSaleService.findSale(cashier, sale.id()).status());

		ApiException excessive = assertThrows(ApiException.class, () -> pointOfSaleService.requestReturn(cashier, sale.id(),
				new PosReturnDraft(UUID.randomUUID(), "Segundo retorno no permitido", List.of(new PosReturnLineDraft(saleLine.id(), 1)))));
		assertEquals("B04_SALE_STATE_INVALID", excessive.code());
	}

	private CatalogProduct inventoryProduct(SessionPrincipal administrator, String prefix) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return catalogService.createProduct(administrator,
				new CatalogProductDraft(null, prefix + suffix, "Producto " + suffix, null, new BigDecimal("5.00"),
						"ACTIVO_NO_PUBLICADO", true, null));
	}

	private PosSale completedSale(SessionPrincipal cashier, UUID productId, int quantity, BigDecimal total) {
		PosSale sale = pointOfSaleService.createSale(cashier,
				new PosSaleDraft(UUID.randomUUID(), List.of(new PosSaleLineDraft(productId, quantity))));
		return pointOfSaleService.confirmSale(cashier, sale.id(), new PosSaleConfirmationDraft("EFECTIVO", total));
	}

	private int available(SessionPrincipal administrator, UUID productId) {
		return stockService.listBalances(administrator).stream().filter(balance -> balance.productId().equals(productId)).findFirst().orElseThrow()
				.availableQuantity();
	}

	private SessionPrincipal administrator() {
		String password = System.getenv("AV_DEMO_AURORA_PASSWORD");
		assertNotNull(password, "La prueba requiere AV_DEMO_AURORA_PASSWORD local.");
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal cashier(SessionPrincipal source) {
		TenantSession session = source.session();
		return new SessionPrincipal(new TenantSession(session.sessionId(), session.accountId(), session.tenantId(), session.tenantSlug(),
				session.tenantCommercialName(), session.tenantStatus(), session.accountType(), List.of("CAJERO"),
				session.sessionTokenHash(), Instant.now().plusSeconds(3600)));
	}
}
