package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProductDraft(
		UUID categoryId,
		String sku,
		String title,
		String description,
		BigDecimal salePricePen,
		String status,
		boolean inventoryManaged,
		String imageUrl) {
}
