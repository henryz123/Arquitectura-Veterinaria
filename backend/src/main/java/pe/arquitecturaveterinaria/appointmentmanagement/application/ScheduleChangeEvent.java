package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** Administrative, tenant-local audit projection without internal identity data. */
public record ScheduleChangeEvent(UUID id, UUID requestId, String actionCode, Instant previousStartsAt, Instant newStartsAt,
		String reason, Instant createdAt) {
}
