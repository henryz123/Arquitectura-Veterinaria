package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosReturnReceiptLineDraft(UUID returnLineId, Integer receivedQuantity) {
}
