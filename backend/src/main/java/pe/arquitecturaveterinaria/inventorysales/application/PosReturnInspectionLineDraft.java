package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosReturnInspectionLineDraft(UUID returnLineId, Integer restockedQuantity) {
}
