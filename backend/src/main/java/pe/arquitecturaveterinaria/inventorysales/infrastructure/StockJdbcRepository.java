package pe.arquitecturaveterinaria.inventorysales.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.inventorysales.application.StockBalance;
import pe.arquitecturaveterinaria.inventorysales.application.StockMovement;

/** JDBC persistence for the V024 stock balance and append-only ledger. */
@Repository
public class StockJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public StockJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void lockOperation(UUID tenantId, UUID operationId) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?::text || ':' || ?::text, 0))",
				resultSet -> null, tenantId, operationId);
	}

	public Optional<InventoryProduct> lockInventoryManagedProduct(UUID productId) {
		return jdbcTemplate.query("""
				select id, inventory_managed
				from inventory_sales.product
				where id = ?
				for update
				""", (resultSet, rowNumber) -> new InventoryProduct(resultSet.getObject("id", UUID.class),
				resultSet.getBoolean("inventory_managed")), productId).stream().findFirst();
	}

	public Optional<StockBalance> lockBalance(UUID productId) {
		return jdbcTemplate.query("""
				select balance.id, balance.tenant_id, balance.product_id, product.sku, product.title as product_title,
				       balance.available_quantity, balance.version, balance.updated_at
				from inventory_sales.stock_balance balance
				join inventory_sales.product product on product.tenant_id = balance.tenant_id and product.id = balance.product_id
				where balance.product_id = ?
				for update of balance
				""", this::mapBalance, productId).stream().findFirst();
	}

	public Optional<StockMovement> findMovementByOperation(UUID operationId) {
		return jdbcTemplate.query("""
				select movement.id, movement.tenant_id, movement.product_id, product.title as product_title, movement.movement_type,
				       movement.quantity_delta, movement.quantity_before, movement.quantity_after, movement.operation_id,
				       movement.reason, movement.created_at
				from inventory_sales.stock_movement movement
				join inventory_sales.product product on product.tenant_id = movement.tenant_id and product.id = movement.product_id
				where movement.operation_id = ?
				""", this::mapMovement, operationId).stream().findFirst();
	}

	public StockBalance createBalance(UUID tenantId, UUID productId, int availableQuantity) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.stock_balance (tenant_id, product_id, available_quantity, version)
				values (?, ?, ?, 1)
				returning id, tenant_id, product_id, null::varchar as sku, null::varchar as product_title,
				          available_quantity, version, updated_at
				""", this::mapBalance, tenantId, productId, availableQuantity);
	}

	public StockBalance updateBalance(StockBalance current, int availableQuantity) {
		return jdbcTemplate.query("""
				update inventory_sales.stock_balance
				set available_quantity = ?, version = version + 1, updated_at = statement_timestamp()
				where id = ? and version = ?
				returning id, tenant_id, product_id, null::varchar as sku, null::varchar as product_title,
				          available_quantity, version, updated_at
				""", this::mapBalance, availableQuantity, current.id(), current.version()).stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("El saldo cambió durante una operación serializada."));
	}

	public StockMovement insertMovement(UUID tenantId, UUID productId, String movementType, int quantityDelta, int quantityBefore,
			int quantityAfter, UUID operationId, String reason, UUID actorPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.stock_movement (
				    tenant_id, product_id, movement_type, quantity_delta, quantity_before, quantity_after,
				    operation_id, reason, actor_principal_id
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, product_id, null::varchar as product_title, movement_type, quantity_delta,
				          quantity_before, quantity_after, operation_id, reason, created_at
				""", this::mapMovement, tenantId, productId, movementType, quantityDelta, quantityBefore, quantityAfter,
				operationId, reason, actorPrincipalId);
	}

	public List<StockBalance> listBalances() {
		return jdbcTemplate.query("""
				select balance.id, balance.tenant_id, balance.product_id, product.sku, product.title as product_title,
				       balance.available_quantity, balance.version, balance.updated_at
				from inventory_sales.stock_balance balance
				join inventory_sales.product product on product.tenant_id = balance.tenant_id and product.id = balance.product_id
				order by product.title, product.sku, balance.id
				""", this::mapBalance);
	}

	public List<StockMovement> listMovements(UUID productId) {
		return jdbcTemplate.query("""
				select movement.id, movement.tenant_id, movement.product_id, product.title as product_title, movement.movement_type,
				       movement.quantity_delta, movement.quantity_before, movement.quantity_after, movement.operation_id,
				       movement.reason, movement.created_at
				from inventory_sales.stock_movement movement
				join inventory_sales.product product on product.tenant_id = movement.tenant_id and product.id = movement.product_id
				where (cast(? as uuid) is null or movement.product_id = cast(? as uuid))
				order by movement.created_at desc, movement.id desc
				""", this::mapMovement, productId, productId);
	}

	private StockBalance mapBalance(ResultSet resultSet, int rowNumber) throws SQLException {
		return new StockBalance(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("product_id", UUID.class), resultSet.getString("sku"), resultSet.getString("product_title"),
				resultSet.getInt("available_quantity"), resultSet.getInt("version"), instant(resultSet, "updated_at"));
	}

	private StockMovement mapMovement(ResultSet resultSet, int rowNumber) throws SQLException {
		return new StockMovement(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("product_id", UUID.class), resultSet.getString("product_title"), resultSet.getString("movement_type"),
				resultSet.getInt("quantity_delta"), resultSet.getInt("quantity_before"), resultSet.getInt("quantity_after"),
				resultSet.getObject("operation_id", UUID.class), resultSet.getString("reason"), instant(resultSet, "created_at"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	public record InventoryProduct(UUID id, boolean inventoryManaged) {
	}
}
