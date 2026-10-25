package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.math.BigDecimal;

/** Editing a service intentionally cannot change its clinical/grooming classification. */
public record OfferedServiceUpdateDraft(String name, String publicDescription, int durationMinutes,
		BigDecimal referencePrice, boolean published, String status) {
}
