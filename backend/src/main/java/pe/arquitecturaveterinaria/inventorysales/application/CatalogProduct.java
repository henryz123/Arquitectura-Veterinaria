package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Product projection for the protected staff catalog; it is not a public-store projection. */
public record CatalogProduct(
		UUID id,
		UUID tenantId,
		UUID categoryId,
		String categoryName,
		String sku,
		String title,
		String description,
		BigDecimal salePricePen,
		String status,
		boolean inventoryManaged,
		String imageUrl,
		Instant createdAt,
		Instant updatedAt) {
}
