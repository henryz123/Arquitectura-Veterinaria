package pe.arquitecturaveterinaria.inventorysales.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.inventorysales.application.CatalogCategory;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogCategoryDraft;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogProduct;
import pe.arquitecturaveterinaria.inventorysales.application.CatalogProductDraft;

/** JDBC adapter for the V023 tenant-local category and product catalog. */
@Repository
public class CatalogJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public CatalogJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<CatalogCategory> listCategories(String query, String status) {
		return jdbcTemplate.query("""
				select id, tenant_id, name, status, created_at, updated_at
				from inventory_sales.product_category
				where (cast(? as varchar) is null or status = cast(? as varchar))
				  and (cast(? as varchar) is null or lower(name) like '%' || lower(cast(? as varchar)) || '%')
				order by status, name, id
				""", this::mapCategory, status, status, query, query);
	}

	public Optional<CatalogCategory> createCategory(UUID tenantId, UUID actorPrincipalId, CatalogCategoryDraft draft) {
		return jdbcTemplate.query("""
				insert into inventory_sales.product_category (tenant_id, name, status, created_by_principal_id)
				values (?, ?, ?, ?)
				on conflict (tenant_id, lower(btrim(name))) do nothing
				returning id, tenant_id, name, status, created_at, updated_at
				""", this::mapCategory, tenantId, draft.name(), draft.status(), actorPrincipalId).stream().findFirst();
	}

	public Optional<CatalogCategory> findCategory(UUID categoryId) {
		return jdbcTemplate.query("""
				select id, tenant_id, name, status, created_at, updated_at
				from inventory_sales.product_category
				where id = ?
				""", this::mapCategory, categoryId).stream().findFirst();
	}

	public Optional<CatalogCategory> updateCategory(UUID tenantId, UUID categoryId, CatalogCategoryDraft draft) {
		return jdbcTemplate.query("""
				update inventory_sales.product_category target
				set name = ?, status = ?, updated_at = statement_timestamp()
				where tenant_id = ? and id = ?
				  and not exists (
				      select 1 from inventory_sales.product_category contender
				      where contender.tenant_id = target.tenant_id
				        and lower(btrim(contender.name)) = lower(btrim(?))
				        and contender.id <> target.id
				  )
				returning id, tenant_id, name, status, created_at, updated_at
				""", this::mapCategory, draft.name(), draft.status(), tenantId, categoryId, draft.name()).stream().findFirst();
	}

	public List<CatalogProduct> listProducts(String query, String status, UUID categoryId) {
		return jdbcTemplate.query("""
				select p.id, p.tenant_id, p.category_id, c.name as category_name, p.sku, p.title, p.description,
				       p.sale_price_pen, p.status, p.inventory_managed, p.image_url, p.created_at, p.updated_at
				from inventory_sales.product p
				left join inventory_sales.product_category c on c.tenant_id = p.tenant_id and c.id = p.category_id
				where (cast(? as varchar) is null or p.status = cast(? as varchar))
				  and (cast(? as uuid) is null or p.category_id = cast(? as uuid))
				  and (cast(? as varchar) is null or lower(p.sku) like '%' || lower(cast(? as varchar)) || '%'
				       or lower(p.title) like '%' || lower(cast(? as varchar)) || '%')
				order by p.status, p.title, p.sku, p.id
				""", this::mapProduct, status, status, categoryId, categoryId, query, query, query);
	}

	public Optional<CatalogProduct> createProduct(UUID tenantId, UUID actorPrincipalId, CatalogProductDraft draft) {
		return jdbcTemplate.query("""
				insert into inventory_sales.product (
				    tenant_id, category_id, sku, title, description, sale_price_pen, status, inventory_managed,
				    image_url, created_by_principal_id
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (tenant_id, lower(btrim(sku))) do nothing
				returning id, tenant_id, category_id, null::varchar as category_name, sku, title, description,
				          sale_price_pen, status, inventory_managed, image_url, created_at, updated_at
				""", this::mapProduct, tenantId, draft.categoryId(), draft.sku(), draft.title(), draft.description(),
				draft.salePricePen(), draft.status(), draft.inventoryManaged(), draft.imageUrl(), actorPrincipalId).stream().findFirst();
	}

	public Optional<CatalogProduct> updateProduct(UUID tenantId, UUID productId, CatalogProductDraft draft) {
		return jdbcTemplate.query("""
				update inventory_sales.product target
				set category_id = ?, sku = ?, title = ?, description = ?, sale_price_pen = ?, status = ?,
				    inventory_managed = ?, image_url = ?, updated_at = statement_timestamp()
				where tenant_id = ? and id = ?
				  and not exists (
				      select 1 from inventory_sales.product contender
				      where contender.tenant_id = target.tenant_id
				        and lower(btrim(contender.sku)) = lower(btrim(?))
				        and contender.id <> target.id
				  )
				returning id, tenant_id, category_id, null::varchar as category_name, sku, title, description,
				          sale_price_pen, status, inventory_managed, image_url, created_at, updated_at
				""", this::mapProduct, draft.categoryId(), draft.sku(), draft.title(), draft.description(),
				draft.salePricePen(), draft.status(), draft.inventoryManaged(), draft.imageUrl(), tenantId, productId, draft.sku())
				.stream().findFirst();
	}

	public Optional<CatalogProduct> findProduct(UUID productId) {
		return jdbcTemplate.query("""
				select id, tenant_id, category_id, null::varchar as category_name, sku, title, description,
				       sale_price_pen, status, inventory_managed, image_url, created_at, updated_at
				from inventory_sales.product
				where id = ?
				""", this::mapProduct, productId)
				.stream().findFirst();
	}

	private CatalogCategory mapCategory(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CatalogCategory(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("name"), resultSet.getString("status"), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"));
	}

	private CatalogProduct mapProduct(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CatalogProduct(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("category_id", UUID.class), resultSet.getString("category_name"), resultSet.getString("sku"),
				resultSet.getString("title"), resultSet.getString("description"), resultSet.getBigDecimal("sale_price_pen"),
				resultSet.getString("status"), resultSet.getBoolean("inventory_managed"), resultSet.getString("image_url"),
				instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
