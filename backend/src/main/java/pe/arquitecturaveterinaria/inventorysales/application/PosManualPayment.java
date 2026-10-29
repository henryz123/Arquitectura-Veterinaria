package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PosManualPayment(UUID id, String paymentMethod, BigDecimal declaredAmountPen, String status,
		UUID declaredByPrincipalId, UUID validatedByPrincipalId, Instant declaredAt, Instant validatedAt) {
}
