package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PosSaleLine(UUID id, UUID productId, String productSkuSnapshot, String productTitleSnapshot,
		boolean inventoryManaged, int quantity, BigDecimal unitPricePen, BigDecimal subtotalPen) {
}
