package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** The effective, person-bound appointment interval created only by confirmation. */
public record ScheduleReservation(UUID id, UUID tenantId, UUID requestId, UUID professionalPrincipalId, Instant startsAt,
		Instant endsAt, String status, boolean current, int version, UUID confirmedByPrincipalId, Instant createdAt,
		Instant updatedAt) {
}
