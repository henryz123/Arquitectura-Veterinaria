package pe.arquitecturaveterinaria.inventorysales.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import pe.arquitecturaveterinaria.inventorysales.application.StockAdjustment;
import pe.arquitecturaveterinaria.inventorysales.application.StockAdjustmentDraft;
import pe.arquitecturaveterinaria.inventorysales.application.StockBalance;
import pe.arquitecturaveterinaria.inventorysales.application.StockMovement;
import pe.arquitecturaveterinaria.inventorysales.application.StockService;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

/** Protected B04 stock endpoints; tenant and admin authority are session-derived. */
@RestController
@RequestMapping("/api/v1/inventory")
@Validated
public class StockController {

	private final StockService service;

	public StockController(StockService service) {
		this.service = service;
	}

	@GetMapping("/stock")
	ApiEnvelope<List<StockBalance>> listBalances(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listBalances(principal));
	}

	@GetMapping("/stock/movements")
	ApiEnvelope<List<StockMovement>> listMovements(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) UUID productId) {
		return ApiEnvelope.of(service.listMovements(principal, productId));
	}

	@PostMapping("/stock/products/{productId}/adjustments")
	ResponseEntity<ApiEnvelope<StockAdjustment>> adjust(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID productId, @Valid @RequestBody StockAdjustmentRequest request) {
		StockAdjustment adjustment = service.adjust(principal, productId,
				new StockAdjustmentDraft(request.quantityDelta(), request.reason(), request.operationId()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/inventory/stock/movements/" + adjustment.movement().id()))
				.body(ApiEnvelope.of(adjustment));
	}

	public record StockAdjustmentRequest(
			@NotNull Integer quantityDelta,
			@NotBlank @Size(min = 3, max = 500) String reason,
			@NotNull UUID operationId) {
	}
}
