package pe.arquitecturaveterinaria.inventorysales.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.inventorysales.infrastructure.CatalogJdbcRepository;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** B04 category and product vertical. Stock and commercial operations remain in later increments. */
@Service
public class CatalogService {

	private static final Set<String> CATEGORY_STATUSES = Set.of("ACTIVA", "INACTIVA");
	private static final Set<String> PRODUCT_STATUSES = Set.of("ACTIVO_PUBLICADO", "ACTIVO_NO_PUBLICADO", "INACTIVO");

	private final CatalogJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final InventoryAccessPolicy accessPolicy = new InventoryAccessPolicy();

	public CatalogService(CatalogJdbcRepository repository, TenantContextJdbc tenantContext) {
		this.repository = repository;
		this.tenantContext = tenantContext;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<CatalogCategory> listCategories(SessionPrincipal actor, String query, String status) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		return repository.listCategories(optional(query, 120), status == null ? null : categoryStatus(status));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CatalogCategory createCategory(SessionPrincipal actor, CatalogCategoryDraft command) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		return repository.createCategory(actor.session().tenantId(), actor.session().accountId(), categoryDraft(command))
				.orElseThrow(() -> conflict("B04_CATEGORY_DUPLICATE", "Ya existe una categoría con ese nombre en la veterinaria.", null));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CatalogCategory updateCategory(SessionPrincipal actor, UUID categoryId, CatalogCategoryDraft command) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		CatalogCategoryDraft draft = categoryDraft(command);
		var updated = repository.updateCategory(actor.session().tenantId(), categoryId, draft);
		if (updated.isPresent()) {
			return updated.get();
		}
		if (repository.findCategory(categoryId).isPresent()) {
			throw conflict("B04_CATEGORY_DUPLICATE", "Ya existe una categoría con ese nombre en la veterinaria.", null);
		}
		throw notFound("No se encontró la categoría solicitada.");
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<CatalogProduct> listProducts(SessionPrincipal actor, String query, String status, UUID categoryId) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		return repository.listProducts(optional(query, 160), status == null ? null : productStatus(status), categoryId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CatalogProduct createProduct(SessionPrincipal actor, CatalogProductDraft command) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		CatalogProduct created = repository.createProduct(actor.session().tenantId(), actor.session().accountId(), productDraft(command))
				.orElseThrow(() -> conflict("B04_PRODUCT_DUPLICATE", "El SKU ya existe en esta veterinaria.", null));
		return withCategoryName(created);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CatalogProduct updateProduct(SessionPrincipal actor, UUID productId, CatalogProductDraft command) {
		accessPolicy.requireCatalogManager(actor);
		setTrustedContext(actor);
		CatalogProductDraft draft = productDraft(command);
		var updatedResult = repository.updateProduct(actor.session().tenantId(), productId, draft);
		if (updatedResult.isEmpty()) {
			if (repository.findProduct(productId).isPresent()) {
				throw conflict("B04_PRODUCT_DUPLICATE", "El SKU ya existe en esta veterinaria.", null);
			}
			throw notFound("No se encontró el producto solicitado.");
		}
		CatalogProduct updated = updatedResult.get();
		return withCategoryName(updated);
	}

	private CatalogCategoryDraft categoryDraft(CatalogCategoryDraft command) {
		if (command == null) {
			throw validation("category", "Completa los datos de la categoría.");
		}
		return new CatalogCategoryDraft(required(command.name(), "name", 120), categoryStatus(command.status()));
	}

	private CatalogProductDraft productDraft(CatalogProductDraft command) {
		if (command == null) {
			throw validation("product", "Completa los datos del producto.");
		}
		UUID categoryId = command.categoryId();
		if (categoryId != null) {
			CatalogCategory category = repository.findCategory(categoryId)
					.orElseThrow(() -> notFound("La categoría no pertenece a esta veterinaria."));
			if (!"ACTIVA".equals(category.status())) {
				throw conflict("B04_CATEGORY_INACTIVE", "No se puede asignar una categoría inactiva a un producto.", null);
			}
		}
		return new CatalogProductDraft(categoryId, required(command.sku(), "sku", 64), required(command.title(), "title", 160),
				optional(command.description(), 2000), price(command.salePricePen()), productStatus(command.status()),
				command.inventoryManaged(), optionalSecureImageUrl(command.imageUrl()));
	}

	private CatalogProduct withCategoryName(CatalogProduct product) {
		if (product.categoryId() == null) {
			return product;
		}
		CatalogCategory category = repository.findCategory(product.categoryId())
				.orElseThrow(() -> new IllegalStateException("La categoría recién asociada dejó de ser visible."));
		return new CatalogProduct(product.id(), product.tenantId(), product.categoryId(), category.name(), product.sku(), product.title(),
				product.description(), product.salePricePen(), product.status(), product.inventoryManaged(), product.imageUrl(),
				product.createdAt(), product.updatedAt());
	}

	private BigDecimal price(BigDecimal value) {
		if (value == null || value.signum() < 0 || value.scale() > 2) {
			throw validation("salePricePen", "El precio debe ser un importe PEN no negativo con hasta dos decimales.");
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private String categoryStatus(String value) {
		return enumValue(value, "status", CATEGORY_STATUSES, "El estado de categoría no es válido.");
	}

	private String productStatus(String value) {
		return enumValue(value, "status", PRODUCT_STATUSES, "El estado de producto no es válido.");
	}

	private String enumValue(String value, String field, Set<String> accepted, String message) {
		String normalized = required(value, field, 32);
		if (!accepted.contains(normalized)) {
			throw validation(field, message);
		}
		return normalized;
	}

	private String required(String value, String field, int maximumLength) {
		String normalized = optional(value, maximumLength);
		if (normalized == null) {
			throw validation(field, "El campo es obligatorio.");
		}
		return normalized;
	}

	private String optional(String value, int maximumLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > maximumLength) {
			throw validation("value", "El valor supera la longitud permitida.");
		}
		return normalized;
	}

	private String optionalSecureImageUrl(String value) {
		String normalized = optional(value, 500);
		if (normalized == null) {
			return null;
		}
		try {
			URI uri = new URI(normalized);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
				throw validation("imageUrl", "La imagen debe usar una URL HTTPS sin credenciales.");
			}
			return normalized;
		} catch (URISyntaxException exception) {
			throw validation("imageUrl", "La URL de imagen no es válida.");
		}
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
	}

	private ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "B04_VALIDATION_" + field.toUpperCase(), message);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B04_CATALOG_NOT_FOUND", message);
	}

	private ApiException conflict(String code, String message, Throwable cause) {
		return new ApiException(HttpStatus.CONFLICT, code, message, cause);
	}
}
