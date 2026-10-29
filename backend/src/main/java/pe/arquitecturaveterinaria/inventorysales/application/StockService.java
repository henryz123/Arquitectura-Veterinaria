package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.inventorysales.infrastructure.StockJdbcRepository;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** B04 stock authority: one tenant-local balance and immutable movement per operation. */
@Service
public class StockService {

	private final StockJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final InventoryAccessPolicy accessPolicy = new InventoryAccessPolicy();

	public StockService(StockJdbcRepository repository, TenantContextJdbc tenantContext) {
		this.repository = repository;
		this.tenantContext = tenantContext;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<StockBalance> listBalances(SessionPrincipal actor) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		return repository.listBalances();
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<StockMovement> listMovements(SessionPrincipal actor, UUID productId) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		return repository.listMovements(productId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public StockAdjustment adjust(SessionPrincipal actor, UUID productId, StockAdjustmentDraft command) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		StockAdjustmentDraft draft = draft(command);
		UUID tenantId = actor.session().tenantId();
		repository.lockOperation(tenantId, draft.operationId());
		StockMovement existing = repository.findMovementByOperation(draft.operationId()).orElse(null);
		if (existing != null) {
			if (!existing.productId().equals(productId) || existing.quantityDelta() != draft.quantityDelta()
					|| !existing.reason().equals(draft.reason())) {
				throw conflict("B04_ADJUSTMENT_REPLAY_CONFLICT", "La operación ya fue usada con datos distintos.");
			}
			return new StockAdjustment(balanceForExisting(productId), existing);
		}
		StockJdbcRepository.InventoryProduct product = repository.lockInventoryManagedProduct(productId)
				.orElseThrow(() -> notFound("No se encontró el producto solicitado."));
		if (!product.inventoryManaged()) {
			throw conflict("B04_PRODUCT_NOT_INVENTORIABLE", "El producto no administra existencias.");
		}
		StockBalance current = repository.lockBalance(productId).orElse(null);
		int quantityBefore = current == null ? 0 : current.availableQuantity();
		int quantityAfter;
		try {
			quantityAfter = Math.addExact(quantityBefore, draft.quantityDelta());
		} catch (ArithmeticException exception) {
			throw validation("quantityDelta", "La cantidad excede el rango permitido.");
		}
		if (quantityAfter < 0) {
			throw conflict("B04_STOCK_INSUFFICIENT", "El ajuste no puede dejar existencias negativas.");
		}
		if (current == null && draft.quantityDelta() < 0) {
			throw conflict("B04_STOCK_INSUFFICIENT", "No existe saldo disponible para un ajuste negativo.");
		}
		StockBalance changed = current == null ? repository.createBalance(tenantId, productId, quantityAfter)
				: repository.updateBalance(current, quantityAfter);
		String movementType = current == null ? "ALTA_INICIAL" : draft.quantityDelta() > 0 ? "AJUSTE_POSITIVO" : "AJUSTE_NEGATIVO";
		StockMovement movement = repository.insertMovement(tenantId, productId, movementType, draft.quantityDelta(), quantityBefore,
				quantityAfter, draft.operationId(), draft.reason(), actor.session().accountId());
		return new StockAdjustment(changed, movement);
	}

	private StockBalance balanceForExisting(UUID productId) {
		return repository.lockBalance(productId)
				.orElseThrow(() -> new IllegalStateException("El movimiento idempotente no conserva saldo asociado."));
	}

	private StockAdjustmentDraft draft(StockAdjustmentDraft command) {
		if (command == null || command.operationId() == null) {
			throw validation("operationId", "El identificador de operación es obligatorio.");
		}
		if (command.quantityDelta() == 0 || Math.abs((long) command.quantityDelta()) > 1_000_000L) {
			throw validation("quantityDelta", "La cantidad debe ser una unidad entera distinta de cero dentro del rango permitido.");
		}
		String reason = command.reason() == null ? null : command.reason().trim();
		if (reason == null || reason.length() < 3 || reason.length() > 500) {
			throw validation("reason", "El motivo debe contener entre 3 y 500 caracteres.");
		}
		return new StockAdjustmentDraft(command.quantityDelta(), reason, command.operationId());
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
	}

	private ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "B04_VALIDATION_" + field.toUpperCase(), message);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B04_STOCK_NOT_FOUND", message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
