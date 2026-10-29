package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.List;
import java.util.UUID;

public record PosReturnDraft(UUID operationId, String reason, List<PosReturnLineDraft> lines) {
}
