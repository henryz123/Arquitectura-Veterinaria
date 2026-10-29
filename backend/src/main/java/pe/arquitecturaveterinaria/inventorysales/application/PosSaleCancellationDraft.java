package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosSaleCancellationDraft(UUID operationId, String reason, boolean itemsLeftPremises) {
}
