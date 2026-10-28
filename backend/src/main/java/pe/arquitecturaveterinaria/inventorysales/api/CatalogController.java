package pe.arquitecturaveterinaria.inventorysales.api;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogCategory;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogCategoryDraft;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogProduct;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogProductDraft;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogService;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

/** Protected B04 catalog endpoints. Tenant and authority always come from the session. */
@RestController
@RequestMapping("/api/v1/catalog")
@Validated
public class CatalogController {

	private final CatalogService service;

	public CatalogController(CatalogService service) {
		this.service = service;
	}

	@GetMapping("/categories")
	ApiEnvelope<List<CatalogCategory>> listCategories(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) @Size(max = 120) String query,
			@RequestParam(required = false) @Pattern(regexp = "ACTIVA|INACTIVA") String status) {
		return ApiEnvelope.of(service.listCategories(principal, query, status));
	}

	@PostMapping("/categories")
	ResponseEntity<ApiEnvelope<CatalogCategory>> createCategory(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody CategoryRequest request) {
		CatalogCategory category = service.createCategory(principal, new CatalogCategoryDraft(request.name(), request.status()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/catalog/categories/" + category.id()))
				.body(ApiEnvelope.of(category));
	}

	@PutMapping("/categories/{categoryId}")
	ApiEnvelope<CatalogCategory> updateCategory(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID categoryId,
			@Valid @RequestBody CategoryRequest request) {
		return ApiEnvelope.of(service.updateCategory(principal, categoryId, new CatalogCategoryDraft(request.name(), request.status())));
	}

	@GetMapping("/products")
	ApiEnvelope<List<CatalogProduct>> listProducts(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) @Size(max = 160) String query,
			@RequestParam(required = false) @Pattern(regexp = "ACTIVO_PUBLICADO|ACTIVO_NO_PUBLICADO|INACTIVO") String status,
			@RequestParam(required = false) UUID categoryId) {
		return ApiEnvelope.of(service.listProducts(principal, query, status, categoryId));
	}

	@PostMapping("/products")
	ResponseEntity<ApiEnvelope<CatalogProduct>> createProduct(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody ProductRequest request) {
		CatalogProduct product = service.createProduct(principal, productDraft(request));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/catalog/products/" + product.id()))
				.body(ApiEnvelope.of(product));
	}

	@PutMapping("/products/{productId}")
	ApiEnvelope<CatalogProduct> updateProduct(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID productId,
			@Valid @RequestBody ProductRequest request) {
		return ApiEnvelope.of(service.updateProduct(principal, productId, productDraft(request)));
	}

	private CatalogProductDraft productDraft(ProductRequest request) {
		return new CatalogProductDraft(request.categoryId(), request.sku(), request.title(), request.description(), request.salePricePen(),
				request.status(), request.inventoryManaged(), request.imageUrl());
	}

	public record CategoryRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Pattern(regexp = "ACTIVA|INACTIVA") String status) {
	}

	public record ProductRequest(
			UUID categoryId,
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String title,
			@Size(max = 2000) String description,
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePricePen,
			@NotBlank @Pattern(regexp = "ACTIVO_PUBLICADO|ACTIVO_NO_PUBLICADO|INACTIVO") String status,
			boolean inventoryManaged,
			@Size(max = 500) String imageUrl) {
	}
}
