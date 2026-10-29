package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Cashier-safe projection: commercial data and the current availability only. */
public record PointOfSaleProduct(UUID id, String sku, String title, BigDecimal salePricePen, String status,
		boolean inventoryManaged, Integer availableQuantity) {
}
