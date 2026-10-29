package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosReturnLineDraft(UUID saleLineId, Integer quantity) {
}
