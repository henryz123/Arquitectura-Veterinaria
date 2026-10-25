package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** One local-America/Lima weekly opening interval, where 1 is Monday and 7 is Sunday. */
public record OperatingHours(UUID id, int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal, Instant createdAt,
		Instant updatedAt) {
}
