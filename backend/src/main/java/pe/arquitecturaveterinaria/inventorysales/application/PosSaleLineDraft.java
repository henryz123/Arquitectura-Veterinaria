package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosSaleLineDraft(UUID productId, Integer quantity) {
}
