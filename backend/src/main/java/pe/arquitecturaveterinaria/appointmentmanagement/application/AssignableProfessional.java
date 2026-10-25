package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.util.List;
import java.util.UUID;

/** Minimal B03 projection; it intentionally omits email, names, sessions and tenant identifiers. */
public record AssignableProfessional(UUID principalId, List<String> roles) {
}
