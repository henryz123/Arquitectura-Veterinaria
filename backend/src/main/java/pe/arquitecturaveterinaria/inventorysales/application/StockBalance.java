package pe.arquitecturaveterinaria.inventorysales.application;

import java.time.Instant;
import java.util.UUID;

/** Current tenant-local sellable quantity for one inventory-managed product. */
public record StockBalance(UUID id, UUID tenantId, UUID productId, String sku, String productTitle, int availableQuantity,
		int version, Instant updatedAt) {
}
