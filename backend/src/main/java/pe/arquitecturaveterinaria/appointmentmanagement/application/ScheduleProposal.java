package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** A staff alternative that never occupies an interval until the client accepts it. */
public record ScheduleProposal(UUID id, UUID tenantId, UUID requestId, int version, Instant proposedStartsAt,
		int proposedDurationMinutes, UUID proposedProfessionalPrincipalId, String status, UUID createdByPrincipalId,
		Instant createdAt, Instant decidedAt) {
}
