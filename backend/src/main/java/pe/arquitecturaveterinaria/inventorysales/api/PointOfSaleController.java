package pe.arquitecturaveterinaria.inventorysales.api;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.inventorysales.application.PointOfSaleProduct;
import pe.arquitecturaveterinaria.inventorysales.application.PointOfSaleService;
import pe.arquitecturaveterinaria.inventorysales.application.PosSale;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleCancellation;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleCancellationDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleConfirmationDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleLineDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturn;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturnDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturnInspectionLineDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturnLineDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturnReceiptLineDraft;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

/** Protected B04 in-person sales endpoints. Tenant and role come only from the session. */
@RestController
@RequestMapping("/api/v1/pos")
@Validated
public class PointOfSaleController {

	private final PointOfSaleService service;

	public PointOfSaleController(PointOfSaleService service) {
		this.service = service;
	}

	@GetMapping("/products")
	ApiEnvelope<List<PointOfSaleProduct>> listProducts(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) String query) {
		return ApiEnvelope.of(service.listProducts(principal, query));
	}

	@GetMapping("/sales")
	ApiEnvelope<List<PosSale>> listSales(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listSales(principal));
	}

	@GetMapping("/sales/{saleId}")
	ApiEnvelope<PosSale> findSale(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID saleId) {
		return ApiEnvelope.of(service.findSale(principal, saleId));
	}

	@PostMapping("/sales")
	ResponseEntity<ApiEnvelope<PosSale>> createSale(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody CreateSaleRequest request) {
		PosSale sale = service.createSale(principal, new PosSaleDraft(request.operationId(), request.lines().stream()
				.map(line -> new PosSaleLineDraft(line.productId(), line.quantity())).toList()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/pos/sales/" + sale.id()))
				.body(ApiEnvelope.of(sale));
	}

	@PostMapping("/sales/{saleId}/confirmations")
	ApiEnvelope<PosSale> confirmSale(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID saleId,
			@Valid @RequestBody ConfirmSaleRequest request) {
		return ApiEnvelope.of(service.confirmSale(principal, saleId,
				new PosSaleConfirmationDraft(request.paymentMethod(), request.declaredAmountPen())));
	}

	@PostMapping("/sales/{saleId}/cancellations")
	ApiEnvelope<PosSaleCancellation> cancelSale(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID saleId,
			@Valid @RequestBody CancelSaleRequest request) {
		return ApiEnvelope.of(service.cancelSale(principal, saleId,
				new PosSaleCancellationDraft(request.operationId(), request.reason(), request.itemsLeftPremises())));
	}

	@GetMapping("/returns")
	ApiEnvelope<List<PosReturn>> listReturns(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) UUID saleId) {
		return ApiEnvelope.of(service.listReturns(principal, saleId));
	}

	@GetMapping("/returns/{returnId}")
	ApiEnvelope<PosReturn> findReturn(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID returnId) {
		return ApiEnvelope.of(service.findReturn(principal, returnId));
	}

	@PostMapping("/sales/{saleId}/returns")
	ResponseEntity<ApiEnvelope<PosReturn>> requestReturn(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID saleId,
			@Valid @RequestBody CreateReturnRequest request) {
		PosReturn posReturn = service.requestReturn(principal, saleId, new PosReturnDraft(request.operationId(), request.reason(),
				request.lines().stream().map(line -> new PosReturnLineDraft(line.saleLineId(), line.quantity())).toList()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/pos/returns/" + posReturn.id()))
				.body(ApiEnvelope.of(posReturn));
	}

	@PostMapping("/returns/{returnId}/receipts")
	ApiEnvelope<PosReturn> receiveReturn(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID returnId,
			@Valid @RequestBody ReturnReceiptRequest request) {
		return ApiEnvelope.of(service.receiveReturn(principal, returnId,
				request.lines().stream().map(line -> new PosReturnReceiptLineDraft(line.returnLineId(), line.receivedQuantity())).toList()));
	}

	@PostMapping("/returns/{returnId}/inspections")
	ApiEnvelope<PosReturn> inspectReturn(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID returnId,
			@Valid @RequestBody ReturnInspectionRequest request) {
		return ApiEnvelope.of(service.inspectReturn(principal, returnId,
				request.lines().stream().map(line -> new PosReturnInspectionLineDraft(line.returnLineId(), line.restockedQuantity())).toList()));
	}

	@PostMapping("/returns/{returnId}/closures")
	ApiEnvelope<PosReturn> closeReturn(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID returnId) {
		return ApiEnvelope.of(service.closeReturn(principal, returnId));
	}

	public record CreateSaleRequest(@NotNull UUID operationId, @NotEmpty @Size(max = 100) List<@Valid SaleLineRequest> lines) {
	}

	public record SaleLineRequest(@NotNull UUID productId, @NotNull @Positive Integer quantity) {
	}

	public record ConfirmSaleRequest(@NotBlank @Size(max = 16) String paymentMethod,
			@NotNull @DecimalMin(value = "0.00") BigDecimal declaredAmountPen) {
	}

	public record CancelSaleRequest(@NotNull UUID operationId, @NotBlank @Size(min = 3, max = 500) String reason,
			@NotNull Boolean itemsLeftPremises) {
	}

	public record CreateReturnRequest(@NotNull UUID operationId, @NotBlank @Size(min = 3, max = 500) String reason,
			@NotEmpty @Size(max = 100) List<@Valid ReturnLineRequest> lines) {
	}

	public record ReturnLineRequest(@NotNull UUID saleLineId, @NotNull @Positive Integer quantity) {
	}

	public record ReturnReceiptRequest(@NotEmpty @Size(max = 100) List<@Valid ReturnReceiptLineRequest> lines) {
	}

	public record ReturnReceiptLineRequest(@NotNull UUID returnLineId, @NotNull @PositiveOrZero Integer receivedQuantity) {
	}

	public record ReturnInspectionRequest(@NotEmpty @Size(max = 100) List<@Valid ReturnInspectionLineRequest> lines) {
	}

	public record ReturnInspectionLineRequest(@NotNull UUID returnLineId, @NotNull @PositiveOrZero Integer restockedQuantity) {
	}
}
