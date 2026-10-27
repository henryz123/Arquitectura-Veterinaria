package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** CLIENT projection deliberately omits staff identifiers and internal actor data. */
public record ClientScheduleProposal(UUID id, UUID requestId, int version, Instant proposedStartsAt,
		int proposedDurationMinutes, String status, Instant createdAt) {
}
