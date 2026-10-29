package pe.arquitecturaveterinaria.inventorysales.application;

/** Result of an idempotent stock adjustment. */
public record StockAdjustment(StockBalance balance, StockMovement movement) {
}
