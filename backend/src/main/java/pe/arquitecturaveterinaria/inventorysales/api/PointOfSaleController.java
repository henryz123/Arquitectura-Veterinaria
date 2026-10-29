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
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleConfirmationDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleDraft;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleLineDraft;
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

	public record CreateSaleRequest(@NotNull UUID operationId, @NotEmpty @Size(max = 100) List<@Valid SaleLineRequest> lines) {
	}

	public record SaleLineRequest(@NotNull UUID productId, @NotNull @Positive Integer quantity) {
	}

	public record ConfirmSaleRequest(@NotBlank @Size(max = 16) String paymentMethod,
			@NotNull @DecimalMin(value = "0.00") BigDecimal declaredAmountPen) {
	}
}
