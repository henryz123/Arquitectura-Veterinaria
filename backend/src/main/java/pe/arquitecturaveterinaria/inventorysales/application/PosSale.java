package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A tenant-bound internal sale receipt; payment is manual and non-taxable. */
public record PosSale(UUID id, UUID tenantId, UUID cashierPrincipalId, String status, UUID operationId,
		BigDecimal totalAmountPen, Instant createdAt, Instant completedAt, Instant updatedAt,
		List<PosSaleLine> lines, PosManualPayment payment) {
}
