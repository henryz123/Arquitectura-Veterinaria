package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.inventorysales.infrastructure.PointOfSaleJdbcRepository;
import pe.arquitecturaveterinaria.inventorysales.infrastructure.PointOfSaleJdbcRepository.StockBalanceRow;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** B04 POS vertical: draft creation and atomic, idempotent confirmation against V024 stock. */
@Service
public class PointOfSaleService {

	private static final Set<String> PAYMENT_METHODS = Set.of("EFECTIVO", "TRANSFERENCIA", "OTRO");

	private final PointOfSaleJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final InventoryAccessPolicy accessPolicy = new InventoryAccessPolicy();

	public PointOfSaleService(PointOfSaleJdbcRepository repository, TenantContextJdbc tenantContext) {
		this.repository = repository;
		this.tenantContext = tenantContext;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PointOfSaleProduct> listProducts(SessionPrincipal actor, String query) {
		accessPolicy.requireCashier(actor);
		setTrustedContext(actor);
		return repository.listSaleableProducts(optional(query, 160));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosSale createSale(SessionPrincipal actor, PosSaleDraft command) {
		accessPolicy.requireCashier(actor);
		setTrustedContext(actor);
		PosSaleDraft draft = draft(command);
		UUID tenantId = actor.session().tenantId();
		repository.lockOperation(tenantId, draft.operationId());
		PosSale existing = repository.findSaleByOperation(draft.operationId()).orElse(null);
		if (existing != null) {
			if (!sameLines(draft.lines(), repository.listLines(existing.id()))) {
				throw conflict("B04_SALE_REPLAY_CONFLICT", "La clave de operacion ya fue usada con una venta distinta.");
			}
			return detail(existing);
		}

		List<PreparedLine> prepared = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO.setScale(2);
		for (PosSaleLineDraft line : draft.lines()) {
			PointOfSaleProduct product = repository.lockSaleableProduct(line.productId())
					.orElseThrow(() -> notFound("No se encontro el producto solicitado."));
			if (!active(product.status())) {
				throw conflict("B04_PRODUCT_INACTIVE", "No se puede vender un producto inactivo.");
			}
			BigDecimal subtotal = product.salePricePen().multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.UNNECESSARY);
			prepared.add(new PreparedLine(product, line.quantity(), subtotal));
			total = total.add(subtotal).setScale(2, RoundingMode.UNNECESSARY);
		}

		PosSale sale = repository.insertSale(tenantId, actor.session().accountId(), draft.operationId(), total);
		for (PreparedLine line : prepared) {
			repository.insertLine(tenantId, sale.id(), line.product(), line.quantity(), line.subtotal());
		}
		return detail(sale);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public PosSale findSale(SessionPrincipal actor, UUID saleId) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		return detail(repository.findSale(requiredSaleId(saleId)).orElseThrow(() -> notFound("No se encontro la venta solicitada.")));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PosSale> listSales(SessionPrincipal actor) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		return repository.listSales();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosSale confirmSale(SessionPrincipal actor, UUID saleId, PosSaleConfirmationDraft command) {
		accessPolicy.requireCashier(actor);
		setTrustedContext(actor);
		PosSaleConfirmationDraft draft = confirmationDraft(command);
		PosSale sale = repository.lockSale(requiredSaleId(saleId)).orElseThrow(() -> notFound("No se encontro la venta solicitada."));
		if ("COMPLETADA".equals(sale.status())) {
			return detail(sale);
		}
		if (!"PREPARACION".equals(sale.status())) {
			throw conflict("B04_SALE_STATE_INVALID", "La venta no admite confirmacion en su estado actual.");
		}
		if (sale.totalAmountPen().compareTo(draft.declaredAmountPen()) != 0) {
			throw validation("declaredAmountPen", "El importe declarado debe coincidir exactamente con el total de la venta.");
		}

		List<PosSaleLine> lines = repository.listLines(sale.id()).stream()
				.sorted(Comparator.comparing(line -> line.productId().toString())).toList();
		if (lines.isEmpty()) {
			throw conflict("B04_SALE_EMPTY", "La venta no contiene lineas confirmables.");
		}
		for (PosSaleLine line : lines) {
			PointOfSaleProduct currentProduct = repository.lockSaleableProduct(line.productId())
					.orElseThrow(() -> notFound("Un producto de la venta ya no pertenece a esta veterinaria."));
			if (!active(currentProduct.status())) {
				throw conflict("B04_PRODUCT_INACTIVE", "No se puede confirmar una venta con producto inactivo.");
			}
			if (!line.inventoryManaged()) {
				continue;
			}
			StockBalanceRow balance = repository.lockBalance(line.productId())
					.orElseThrow(() -> conflict("B04_STOCK_INSUFFICIENT", "No hay saldo disponible para confirmar la venta."));
			if (balance.availableQuantity() < line.quantity()) {
				throw conflict("B04_STOCK_INSUFFICIENT", "Las existencias no alcanzan para confirmar la venta.");
			}
			int quantityAfter = balance.availableQuantity() - line.quantity();
			repository.updateBalance(balance, quantityAfter);
			repository.insertSaleMovement(actor.session().tenantId(), sale, line, balance.availableQuantity(), quantityAfter,
					movementOperationId(sale.operationId(), line.productId()), actor.session().accountId());
		}
		repository.insertValidatedPayment(actor.session().tenantId(), sale.id(), draft.paymentMethod(), draft.declaredAmountPen(),
				actor.session().accountId());
		return detail(repository.completeSale(sale.id()));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosSaleCancellation cancelSale(SessionPrincipal actor, UUID saleId, PosSaleCancellationDraft command) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		PosSaleCancellationDraft draft = cancellationDraft(command);
		UUID tenantId = actor.session().tenantId();
		repository.lockOperation(tenantId, draft.operationId());
		PosSaleCancellation replay = repository.findCancellationByOperation(draft.operationId()).orElse(null);
		if (replay != null) {
			if (!replay.saleId().equals(requiredSaleId(saleId)) || !sameCancellation(replay, draft)) {
				throw conflict("B04_CANCELLATION_REPLAY_CONFLICT", "La clave de operación ya fue usada para una anulación distinta.");
			}
			return replay;
		}
		PosSale sale = repository.lockSale(requiredSaleId(saleId)).orElseThrow(() -> notFound("No se encontró la venta solicitada."));
		if (!"COMPLETADA".equals(sale.status())) {
			throw conflict("B04_SALE_STATE_INVALID", "Solo una venta completada admite anulación comercial.");
		}
		if (repository.findCancellation(sale.id()).isPresent()) {
			throw conflict("B04_SALE_ALREADY_CANCELLED", "La venta ya tiene una anulación registrada.");
		}
		if (repository.hasReturns(sale.id())) {
			throw conflict("B04_SALE_HAS_RETURNS", "Una venta con devolución registrada se corrige mediante el flujo de devolución.");
		}
		PosSaleCancellation cancellation = repository.insertCancellation(tenantId, sale.id(), draft.operationId(), draft.reason(),
				draft.itemsLeftPremises(), actor.session().accountId());
		if (!draft.itemsLeftPremises()) {
			List<PosSaleLine> lines = repository.listLines(sale.id()).stream().filter(PosSaleLine::inventoryManaged)
					.sorted(Comparator.comparing(line -> line.productId().toString())).toList();
			for (PosSaleLine line : lines) {
				StockBalanceRow balance = repository.lockBalance(line.productId())
						.orElseThrow(() -> conflict("B04_STOCK_BALANCE_MISSING", "Falta el saldo del producto que se anula."));
				int quantityAfter;
				try {
					quantityAfter = Math.addExact(balance.availableQuantity(), line.quantity());
				} catch (ArithmeticException exception) {
					throw conflict("B04_STOCK_OVERFLOW", "El saldo no admite la reposición solicitada.");
				}
				repository.updateBalance(balance, quantityAfter);
				repository.insertReturnMovement(tenantId, sale, null,
						new PosReturnLine(null, line.id(), line.productId(), line.productSkuSnapshot(), line.productTitleSnapshot(),
								line.quantity(), line.quantity(), line.quantity(), "APTA"),
						balance.availableQuantity(), quantityAfter, cancellationMovementOperationId(sale.operationId(), line.productId()),
						"Anulación POS sin salida física " + sale.id(), actor.session().accountId());
			}
		}
		repository.updateSaleStatus(sale.id(), "ANULADA");
		return cancellation;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PosReturn> listReturns(SessionPrincipal actor, UUID saleId) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		return repository.listReturns(saleId).stream().map(this::returnDetail).toList();
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public PosReturn findReturn(SessionPrincipal actor, UUID returnId) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		return returnDetail(repository.findReturn(requiredReturnId(returnId))
				.orElseThrow(() -> returnNotFound("No se encontró la devolución solicitada.")));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosReturn requestReturn(SessionPrincipal actor, UUID saleId, PosReturnDraft command) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		PosReturnDraft draft = returnDraft(command);
		UUID tenantId = actor.session().tenantId();
		repository.lockOperation(tenantId, draft.operationId());
		PosReturn replay = repository.findReturnByOperation(draft.operationId()).orElse(null);
		if (replay != null) {
			PosReturn existing = returnDetail(replay);
			if (!existing.saleId().equals(requiredSaleId(saleId)) || !sameReturn(existing, draft)) {
				throw conflict("B04_RETURN_REPLAY_CONFLICT", "La clave de operación ya fue usada para una devolución distinta.");
			}
			return existing;
		}
		PosSale sale = repository.lockSale(requiredSaleId(saleId)).orElseThrow(() -> notFound("No se encontró la venta solicitada."));
		if (!("COMPLETADA".equals(sale.status()) || "DEVOLUCION_PARCIAL".equals(sale.status()))) {
			throw conflict("B04_SALE_STATE_INVALID", "La venta no admite una nueva devolución en su estado actual.");
		}
		Map<UUID, PosSaleLine> saleLines = new TreeMap<>();
		for (PosSaleLine line : repository.listLines(sale.id())) saleLines.put(line.id(), line);
		for (PosReturnLineDraft line : draft.lines()) {
			PosSaleLine saleLine = saleLines.get(line.saleLineId());
			if (saleLine == null) throw validation("lines", "La devolución contiene una línea ajena a la venta.");
			if (!saleLine.inventoryManaged()) {
				throw validation("lines", "Los servicios sin stock no admiten devolución física de inventario.");
			}
			if (repository.allocatedReturnQuantity(saleLine.id()) + line.quantity() > saleLine.quantity()) {
				throw conflict("B04_RETURN_QUANTITY_EXCEEDED", "La devolución supera la cantidad vendida pendiente de retorno.");
			}
		}
		PosReturn posReturn = repository.insertReturn(tenantId, sale.id(), draft.operationId(), draft.reason(), actor.session().accountId());
		for (PosReturnLineDraft line : draft.lines()) repository.insertReturnLine(tenantId, posReturn.id(), line.saleLineId(), line.quantity());
		return returnDetail(posReturn);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosReturn receiveReturn(SessionPrincipal actor, UUID returnId, List<PosReturnReceiptLineDraft> command) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		PosReturn posReturn = repository.lockReturn(requiredReturnId(returnId))
				.orElseThrow(() -> returnNotFound("No se encontró la devolución solicitada."));
		PosSale sale = repository.lockSale(posReturn.saleId()).orElseThrow(() -> notFound("No se encontró la venta de la devolución."));
		requireReturnableSale(sale);
		if (!"SOLICITADA".equals(posReturn.status())) return returnDetail(posReturn);
		List<PosReturnLine> lines = repository.lockReturnLines(posReturn.id());
		Map<UUID, Integer> received = receiptDraft(command, lines);
		for (PosReturnLine line : lines) repository.updateReturnLineReceipt(line.id(), received.get(line.id()));
		repository.markReturnReceived(posReturn.id(), actor.session().accountId());
		return findReturnAfterChange(posReturn.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosReturn inspectReturn(SessionPrincipal actor, UUID returnId, List<PosReturnInspectionLineDraft> command) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		PosReturn posReturn = repository.lockReturn(requiredReturnId(returnId))
				.orElseThrow(() -> returnNotFound("No se encontró la devolución solicitada."));
		PosSale sale = repository.lockSale(posReturn.saleId()).orElseThrow(() -> notFound("No se encontró la venta de la devolución."));
		requireReturnableSale(sale);
		if (!("RECIBIDA".equals(posReturn.status()))) return returnDetail(posReturn);
		List<PosReturnLine> lines = repository.lockReturnLines(posReturn.id());
		Map<UUID, Integer> restocked = inspectionDraft(command, lines);
		boolean someRestocked = false;
		for (PosReturnLine line : lines) {
			int approvedQuantity = restocked.get(line.id());
			String result = inspectionResult(line.receivedQuantity(), approvedQuantity);
			if (approvedQuantity > 0) {
				StockBalanceRow balance = repository.lockBalance(line.productId())
						.orElseThrow(() -> conflict("B04_STOCK_BALANCE_MISSING", "Falta el saldo del producto devuelto."));
				int quantityAfter;
				try {
					quantityAfter = Math.addExact(balance.availableQuantity(), approvedQuantity);
				} catch (ArithmeticException exception) {
					throw conflict("B04_STOCK_OVERFLOW", "El saldo no admite la reposición inspeccionada.");
				}
				repository.updateBalance(balance, quantityAfter);
				repository.insertReturnMovement(actor.session().tenantId(), sale, posReturn, line, balance.availableQuantity(), quantityAfter,
						returnMovementOperationId(posReturn.id(), line.id()), "Devolución POS apta " + posReturn.id(), actor.session().accountId());
				someRestocked = true;
			}
			repository.updateReturnLineInspection(line.id(), approvedQuantity, result);
		}
		repository.markReturnInspected(posReturn.id(), someRestocked ? "INSPECCIONADA_APTA" : "INSPECCIONADA_NO_APTA",
				actor.session().accountId());
		return findReturnAfterChange(posReturn.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PosReturn closeReturn(SessionPrincipal actor, UUID returnId) {
		accessPolicy.requireCommercialOperator(actor);
		setTrustedContext(actor);
		PosReturn posReturn = repository.lockReturn(requiredReturnId(returnId))
				.orElseThrow(() -> returnNotFound("No se encontró la devolución solicitada."));
		PosSale sale = repository.lockSale(posReturn.saleId()).orElseThrow(() -> notFound("No se encontró la venta de la devolución."));
		requireReturnableSale(sale);
		if ("CERRADA".equals(posReturn.status())) return returnDetail(posReturn);
		if (!("INSPECCIONADA_APTA".equals(posReturn.status()) || "INSPECCIONADA_NO_APTA".equals(posReturn.status()))) {
			throw conflict("B04_RETURN_STATE_INVALID", "La devolución debe inspeccionarse antes de cerrarse.");
		}
		boolean fullyReturned = repository.allInventoryLinesReturned(sale.id());
		repository.closeReturn(posReturn.id(), actor.session().accountId());
		repository.updateSaleStatus(sale.id(), fullyReturned ? "DEVUELTA" : "DEVOLUCION_PARCIAL");
		return findReturnAfterChange(posReturn.id());
	}

	private PosSaleDraft draft(PosSaleDraft command) {
		if (command == null || command.operationId() == null) {
			throw validation("operationId", "El identificador de operacion es obligatorio.");
		}
		if (command.lines() == null || command.lines().isEmpty()) {
			throw validation("lines", "La venta debe contener al menos un producto.");
		}
		TreeMap<UUID, Integer> quantities = new TreeMap<>();
		for (PosSaleLineDraft source : command.lines()) {
			if (source == null || source.productId() == null || source.quantity() == null || source.quantity() <= 0
					|| source.quantity() > 1_000_000) {
				throw validation("lines", "Cada linea requiere producto y una cantidad entera valida.");
			}
			try {
				quantities.merge(source.productId(), source.quantity(), Math::addExact);
			} catch (ArithmeticException exception) {
				throw validation("lines", "La cantidad acumulada excede el rango permitido.");
			}
		}
		if (quantities.size() > 100) {
			throw validation("lines", "La venta admite hasta 100 productos distintos.");
		}
		List<PosSaleLineDraft> normalized = quantities.entrySet().stream()
				.map(entry -> new PosSaleLineDraft(entry.getKey(), entry.getValue())).toList();
		return new PosSaleDraft(command.operationId(), normalized);
	}

	private PosSaleCancellationDraft cancellationDraft(PosSaleCancellationDraft command) {
		if (command == null || command.operationId() == null) {
			throw validation("operationId", "El identificador de operación es obligatorio.");
		}
		return new PosSaleCancellationDraft(command.operationId(), requiredReason(command.reason()), command.itemsLeftPremises());
	}

	private PosReturnDraft returnDraft(PosReturnDraft command) {
		if (command == null || command.operationId() == null) {
			throw validation("operationId", "El identificador de operación es obligatorio.");
		}
		if (command.lines() == null || command.lines().isEmpty() || command.lines().size() > 100) {
			throw validation("lines", "La devolución requiere entre una y 100 líneas.");
		}
		TreeMap<UUID, Integer> quantities = new TreeMap<>();
		for (PosReturnLineDraft line : command.lines()) {
			if (line == null || line.saleLineId() == null || line.quantity() == null || line.quantity() <= 0 || line.quantity() > 1_000_000) {
				throw validation("lines", "Cada línea de devolución requiere una cantidad entera válida.");
			}
			try {
				quantities.merge(line.saleLineId(), line.quantity(), Math::addExact);
			} catch (ArithmeticException exception) {
				throw validation("lines", "La cantidad acumulada excede el rango permitido.");
			}
		}
		return new PosReturnDraft(command.operationId(), requiredReason(command.reason()), quantities.entrySet().stream()
				.map(entry -> new PosReturnLineDraft(entry.getKey(), entry.getValue())).toList());
	}

	private Map<UUID, Integer> receiptDraft(List<PosReturnReceiptLineDraft> command, List<PosReturnLine> expected) {
		if (command == null || command.size() != expected.size()) {
			throw validation("lines", "La recepción debe indicar exactamente las líneas solicitadas.");
		}
		Map<UUID, Integer> quantities = new TreeMap<>();
		for (PosReturnReceiptLineDraft line : command) {
			if (line == null || line.returnLineId() == null || line.receivedQuantity() == null || line.receivedQuantity() < 0) {
				throw validation("lines", "Cada recepción requiere una cantidad entera no negativa.");
			}
			if (quantities.put(line.returnLineId(), line.receivedQuantity()) != null) {
				throw validation("lines", "No se puede repetir una línea de recepción.");
			}
		}
		for (PosReturnLine line : expected) {
			Integer quantity = quantities.get(line.id());
			if (quantity == null || quantity > line.requestedQuantity()) {
				throw validation("lines", "La recepción supera o no identifica la cantidad solicitada.");
			}
		}
		return quantities;
	}

	private Map<UUID, Integer> inspectionDraft(List<PosReturnInspectionLineDraft> command, List<PosReturnLine> expected) {
		if (command == null || command.size() != expected.size()) {
			throw validation("lines", "La inspección debe indicar exactamente las líneas recibidas.");
		}
		Map<UUID, Integer> quantities = new TreeMap<>();
		for (PosReturnInspectionLineDraft line : command) {
			if (line == null || line.returnLineId() == null || line.restockedQuantity() == null || line.restockedQuantity() < 0) {
				throw validation("lines", "Cada inspección requiere una cantidad apta no negativa.");
			}
			if (quantities.put(line.returnLineId(), line.restockedQuantity()) != null) {
				throw validation("lines", "No se puede repetir una línea de inspección.");
			}
		}
		for (PosReturnLine line : expected) {
			Integer quantity = quantities.get(line.id());
			if (quantity == null || quantity > line.receivedQuantity()) {
				throw validation("lines", "La reposición supera o no identifica la cantidad recibida.");
			}
		}
		return quantities;
	}

	private String requiredReason(String source) {
		String value = source == null ? "" : source.trim();
		if (value.length() < 3 || value.length() > 500) {
			throw validation("reason", "El motivo debe tener entre 3 y 500 caracteres.");
		}
		return value;
	}

	private boolean sameCancellation(PosSaleCancellation source, PosSaleCancellationDraft draft) {
		return source.operationId().equals(draft.operationId()) && source.reason().equals(draft.reason())
				&& source.itemsLeftPremises() == draft.itemsLeftPremises();
	}

	private boolean sameReturn(PosReturn source, PosReturnDraft draft) {
		if (!source.operationId().equals(draft.operationId()) || !source.reason().equals(draft.reason())) return false;
		Map<UUID, Integer> actual = new TreeMap<>();
		for (PosReturnLine line : source.lines()) actual.put(line.saleLineId(), line.requestedQuantity());
		Map<UUID, Integer> requested = new TreeMap<>();
		for (PosReturnLineDraft line : draft.lines()) requested.put(line.saleLineId(), line.quantity());
		return actual.equals(requested);
	}

	private PosReturn returnDetail(PosReturn posReturn) {
		return new PosReturn(posReturn.id(), posReturn.tenantId(), posReturn.saleId(), posReturn.operationId(), posReturn.status(),
				posReturn.reason(), posReturn.requestedByPrincipalId(), posReturn.receivedByPrincipalId(),
				posReturn.inspectedByPrincipalId(), posReturn.closedByPrincipalId(), posReturn.createdAt(), posReturn.receivedAt(),
				posReturn.inspectedAt(), posReturn.closedAt(), posReturn.updatedAt(), repository.listReturnLines(posReturn.id()));
	}

	private PosReturn findReturnAfterChange(UUID returnId) {
		return returnDetail(repository.findReturn(returnId).orElseThrow(() -> returnNotFound("No se encontró la devolución solicitada.")));
	}

	private PosSaleConfirmationDraft confirmationDraft(PosSaleConfirmationDraft command) {
		if (command == null) {
			throw validation("payment", "Completa el pago manual de la venta.");
		}
		String paymentMethod = command.paymentMethod() == null ? null : command.paymentMethod().trim();
		if (paymentMethod == null || !PAYMENT_METHODS.contains(paymentMethod)) {
			throw validation("paymentMethod", "El medio de pago manual no es valido.");
		}
		if (command.declaredAmountPen() == null || command.declaredAmountPen().signum() < 0 || command.declaredAmountPen().scale() > 2) {
			throw validation("declaredAmountPen", "El importe declarado debe ser PEN no negativo con hasta dos decimales.");
		}
		return new PosSaleConfirmationDraft(paymentMethod,
				command.declaredAmountPen().setScale(2, RoundingMode.UNNECESSARY));
	}

	private boolean sameLines(List<PosSaleLineDraft> requested, List<PosSaleLine> persisted) {
		Map<UUID, Integer> expected = new TreeMap<>();
		for (PosSaleLineDraft line : requested) {
			expected.put(line.productId(), line.quantity());
		}
		Map<UUID, Integer> actual = new TreeMap<>();
		for (PosSaleLine line : persisted) {
			actual.put(line.productId(), line.quantity());
		}
		return expected.equals(actual);
	}

	private PosSale detail(PosSale sale) {
		return new PosSale(sale.id(), sale.tenantId(), sale.cashierPrincipalId(), sale.status(), sale.operationId(),
				sale.totalAmountPen(), sale.createdAt(), sale.completedAt(), sale.updatedAt(), repository.listLines(sale.id()),
				repository.findPayment(sale.id()).orElse(null));
	}

	private UUID requiredSaleId(UUID saleId) {
		if (saleId == null) {
			throw validation("saleId", "El identificador de venta es obligatorio.");
		}
		return saleId;
	}

	private UUID requiredReturnId(UUID returnId) {
		if (returnId == null) {
			throw validation("returnId", "El identificador de devolución es obligatorio.");
		}
		return returnId;
	}

	private void requireReturnableSale(PosSale sale) {
		if (!("COMPLETADA".equals(sale.status()) || "DEVOLUCION_PARCIAL".equals(sale.status()))) {
			throw conflict("B04_SALE_STATE_INVALID", "La devolución no puede continuar por el estado actual de la venta.");
		}
	}

	private boolean active(String status) {
		return "ACTIVO_PUBLICADO".equals(status) || "ACTIVO_NO_PUBLICADO".equals(status);
	}

	private UUID movementOperationId(UUID saleOperationId, UUID productId) {
		return UUID.nameUUIDFromBytes(("POS:" + saleOperationId + ":" + productId).getBytes(StandardCharsets.UTF_8));
	}

	private UUID cancellationMovementOperationId(UUID saleOperationId, UUID productId) {
		return UUID.nameUUIDFromBytes(("POS-CANCEL:" + saleOperationId + ":" + productId).getBytes(StandardCharsets.UTF_8));
	}

	private UUID returnMovementOperationId(UUID returnId, UUID returnLineId) {
		return UUID.nameUUIDFromBytes(("POS-RETURN:" + returnId + ":" + returnLineId).getBytes(StandardCharsets.UTF_8));
	}

	private String inspectionResult(int receivedQuantity, int restockedQuantity) {
		if (restockedQuantity == 0) return "NO_APTA";
		return restockedQuantity == receivedQuantity ? "APTA" : "PARCIAL";
	}

	private String optional(String value, int maximumLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > maximumLength) {
			throw validation("query", "El criterio de busqueda supera la longitud permitida.");
		}
		return normalized;
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
	}

	private ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "B04_VALIDATION_" + field.toUpperCase(), message);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B04_SALE_NOT_FOUND", message);
	}

	private ApiException returnNotFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B04_RETURN_NOT_FOUND", message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private record PreparedLine(PointOfSaleProduct product, int quantity, BigDecimal subtotal) {
	}
}
