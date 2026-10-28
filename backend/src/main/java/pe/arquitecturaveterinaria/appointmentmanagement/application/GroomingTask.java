package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** Minimal task projection for the assigned grooming worker, without clinical data. */
public record GroomingTask(UUID reservationId, UUID requestId, String serviceName, Instant startsAt, Instant endsAt,
		String reservationStatus, GroomingAttention attention) {
}
