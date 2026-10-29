package pe.arquitecturaveterinaria.inventorysales.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PosReturn(UUID id, UUID tenantId, UUID saleId, UUID operationId, String status, String reason,
		UUID requestedByPrincipalId, UUID receivedByPrincipalId, UUID inspectedByPrincipalId, UUID closedByPrincipalId,
		Instant createdAt, Instant receivedAt, Instant inspectedAt, Instant closedAt, Instant updatedAt, List<PosReturnLine> lines) {
}
