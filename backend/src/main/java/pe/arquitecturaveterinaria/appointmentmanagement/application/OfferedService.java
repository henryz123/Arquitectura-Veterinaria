package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tenant-local public service definition for clinical appointments or grooming. */
public record OfferedService(UUID id, UUID tenantId, String serviceType, String name, String publicDescription,
		int durationMinutes, BigDecimal referencePrice, boolean published, String status, Instant createdAt, Instant updatedAt) {
}
