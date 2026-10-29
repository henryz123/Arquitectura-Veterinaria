package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;

public record PosSaleConfirmationDraft(String paymentMethod, BigDecimal declaredAmountPen) {
}
