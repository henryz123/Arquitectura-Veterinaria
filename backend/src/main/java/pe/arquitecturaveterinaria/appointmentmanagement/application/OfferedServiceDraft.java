package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.math.BigDecimal;

/** Validated values used exclusively by the JDBC adapter. */
public record OfferedServiceDraft(String serviceType, String name, String publicDescription, int durationMinutes,
		BigDecimal referencePrice, boolean published) {
}
