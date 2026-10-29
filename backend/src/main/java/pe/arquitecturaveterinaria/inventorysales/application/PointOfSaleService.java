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
		accessPolicy.requireCashier(actor);
		setTrustedContext(actor);
		return detail(repository.findSale(requiredSaleId(saleId)).orElseThrow(() -> notFound("No se encontro la venta solicitada.")));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PosSale> listSales(SessionPrincipal actor) {
		accessPolicy.requireCashier(actor);
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

	private boolean active(String status) {
		return "ACTIVO_PUBLICADO".equals(status) || "ACTIVO_NO_PUBLICADO".equals(status);
	}

	private UUID movementOperationId(UUID saleOperationId, UUID productId) {
		return UUID.nameUUIDFromBytes(("POS:" + saleOperationId + ":" + productId).getBytes(StandardCharsets.UTF_8));
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

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private record PreparedLine(PointOfSaleProduct product, int quantity, BigDecimal subtotal) {
	}
}
