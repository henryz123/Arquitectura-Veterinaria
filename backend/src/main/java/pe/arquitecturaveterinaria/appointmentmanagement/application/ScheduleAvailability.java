package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.util.UUID;

/** Result of a non-reserving availability check; confirmation repeats the same checks atomically. */
public record ScheduleAvailability(UUID professionalPrincipalId, UUID serviceId, Instant startsAt, Instant endsAt,
		boolean available, String reasonCode) {
}
