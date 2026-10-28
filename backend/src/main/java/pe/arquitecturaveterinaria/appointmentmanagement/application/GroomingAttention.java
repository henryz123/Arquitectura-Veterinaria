package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** Operational execution record for one confirmed grooming reservation. */
public record GroomingAttention(UUID id, UUID reservationId, UUID groomerPrincipalId, Instant startedAt, Instant completedAt,
		String result, String operationalNotes, Instant createdAt, Instant updatedAt) {
}
