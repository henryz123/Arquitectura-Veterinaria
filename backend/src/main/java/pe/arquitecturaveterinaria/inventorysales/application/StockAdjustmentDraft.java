package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

/** Command for initial stock and later authorized manual adjustments. */
public record StockAdjustmentDraft(int quantityDelta, String reason, UUID operationId) {
}
