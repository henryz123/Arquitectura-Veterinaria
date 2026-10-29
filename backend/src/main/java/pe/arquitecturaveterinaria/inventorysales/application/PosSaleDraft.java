package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.List;
import java.util.UUID;

public record PosSaleDraft(UUID operationId, List<PosSaleLineDraft> lines) {
}
