package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** A tenant-local request. Pending requests deliberately do not reserve time. */
public record ScheduleRequest(UUID id, UUID tenantId, UUID requesterPrincipalId, UUID ownerId, UUID petId, UUID serviceId,
		Instant requestedStartsAt, int requestedDurationMinutes, String status, String observation, UUID idempotencyKey,
		Instant createdAt, Instant updatedAt) {
}
