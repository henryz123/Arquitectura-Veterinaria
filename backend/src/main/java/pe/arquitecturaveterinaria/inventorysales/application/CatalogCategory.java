package pe.arquitecturaveterinaria.inventorysales.application;

import java.time.Instant;
import java.util.UUID;

/** Tenant-local category used only by the B04 product catalog. */
public record CatalogCategory(
		UUID id,
		UUID tenantId,
		String name,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
