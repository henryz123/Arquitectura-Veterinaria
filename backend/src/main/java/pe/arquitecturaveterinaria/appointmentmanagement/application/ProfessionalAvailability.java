package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** A weekly local availability interval for an active assignable professional. */
public record ProfessionalAvailability(UUID id, UUID professionalPrincipalId, int weekday, LocalTime startsAtLocal,
		LocalTime endsAtLocal, Instant createdAt, Instant updatedAt) {
}
