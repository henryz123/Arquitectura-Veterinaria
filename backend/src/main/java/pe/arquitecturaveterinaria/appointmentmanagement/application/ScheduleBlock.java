package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** Tenant-local exceptional closure or an absence of one assignable professional. */
public record ScheduleBlock(UUID id, String blockScope, UUID professionalPrincipalId, Instant startsAt, Instant endsAt,
		String reason, Instant createdAt) {
}
