package pe.arquitecturaveterinaria.inventorysales.application;

import java.time.Instant;
import java.util.UUID;

/** Immutable explanation for a stock balance change. */
public record StockMovement(UUID id, UUID tenantId, UUID productId, String productTitle, String movementType, int quantityDelta,
		int quantityBefore, int quantityAfter, UUID operationId, String reason, Instant createdAt) {
}
