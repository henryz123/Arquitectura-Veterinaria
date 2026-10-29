package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.UUID;

public record PosReturnLine(UUID id, UUID saleLineId, UUID productId, String productSkuSnapshot, String productTitleSnapshot,
		int requestedQuantity, int receivedQuantity, int restockedQuantity, String inspectionResult) {
}
