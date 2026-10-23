package pe.arquitecturaveterinaria.identityaccess.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;

public record TenantSession(
		UUID sessionId,
		UUID accountId,
		UUID tenantId,
		String tenantSlug,
		String tenantCommercialName,
		String tenantStatus,
		AccountType accountType,
		List<String> roles,
		String sessionTokenHash,
		Instant expiresAt) {
}
