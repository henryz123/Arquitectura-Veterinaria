package pe.arquitecturaveterinaria.inventorysales.application;

import java.time.Instant;
import java.util.UUID;

public record PosSaleCancellation(UUID id, UUID saleId, UUID operationId, String reason, boolean itemsLeftPremises,
		boolean stockRestocked, UUID cancelledByPrincipalId, Instant createdAt) {
}
