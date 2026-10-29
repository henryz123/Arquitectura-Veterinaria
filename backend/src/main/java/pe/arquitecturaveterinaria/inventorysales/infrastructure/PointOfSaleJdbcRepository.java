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

import pe.arquitecturaveterinaria.inventorysales.application.PointOfSaleProduct;
import pe.arquitecturaveterinaria.inventorysales.application.PosManualPayment;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturn;
import pe.arquitecturaveterinaria.inventorysales.application.PosReturnLine;
import pe.arquitecturaveterinaria.inventorysales.application.PosSale;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleCancellation;
import pe.arquitecturaveterinaria.inventorysales.application.PosSaleLine;

/** JDBC adapter for V025. All callers set the trusted tenant GUC in their transaction. */
@Repository
public class PointOfSaleJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public PointOfSaleJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void lockOperation(UUID tenantId, UUID operationId) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?::text || ':' || ?::text, 0))",
				resultSet -> null, tenantId, operationId);
	}

	public List<PointOfSaleProduct> listSaleableProducts(String query) {
		return jdbcTemplate.query("""
				select p.id, p.sku, p.title, p.sale_price_pen, p.status, p.inventory_managed,
				       b.available_quantity
				from inventory_sales.product p
				left join inventory_sales.stock_balance b
				  on b.tenant_id = p.tenant_id and b.product_id = p.id
				where p.status in ('ACTIVO_PUBLICADO', 'ACTIVO_NO_PUBLICADO')
				  and (cast(? as varchar) is null or lower(p.sku) like '%' || lower(cast(? as varchar)) || '%'
				       or lower(p.title) like '%' || lower(cast(? as varchar)) || '%')
				order by p.title, p.sku, p.id
				""", this::mapSaleableProduct, query, query, query);
	}

	public Optional<PointOfSaleProduct> lockSaleableProduct(UUID productId) {
		return jdbcTemplate.query("""
				select p.id, p.sku, p.title, p.sale_price_pen, p.status, p.inventory_managed,
				       b.available_quantity
				from inventory_sales.product p
				left join inventory_sales.stock_balance b
				  on b.tenant_id = p.tenant_id and b.product_id = p.id
				where p.id = ?
				for update of p
				""", this::mapSaleableProduct, productId).stream().findFirst();
	}

	public Optional<PosSale> findSaleByOperation(UUID operationId) {
		return jdbcTemplate.query("""
				select id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				       created_at, completed_at, updated_at
				from inventory_sales.pos_sale where operation_id = ?
				""", this::mapSale, operationId).stream().findFirst();
	}

	public Optional<PosSale> lockSale(UUID saleId) {
		return jdbcTemplate.query("""
				select id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				       created_at, completed_at, updated_at
				from inventory_sales.pos_sale where id = ? for update
				""", this::mapSale, saleId).stream().findFirst();
	}

	public Optional<PosSale> findSale(UUID saleId) {
		return jdbcTemplate.query("""
				select id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				       created_at, completed_at, updated_at
				from inventory_sales.pos_sale where id = ?
				""", this::mapSale, saleId).stream().findFirst();
	}

	public List<PosSale> listSales() {
		return jdbcTemplate.query("""
				select id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				       created_at, completed_at, updated_at
				from inventory_sales.pos_sale
				order by created_at desc, id desc
				""", this::mapSale);
	}

	public PosSale insertSale(UUID tenantId, UUID cashierPrincipalId, UUID operationId, BigDecimal totalAmountPen) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.pos_sale (tenant_id, cashier_principal_id, operation_id, total_amount_pen)
				values (?, ?, ?, ?)
				returning id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				          created_at, completed_at, updated_at
				""", this::mapSale, tenantId, cashierPrincipalId, operationId, totalAmountPen);
	}

	public PosSaleLine insertLine(UUID tenantId, UUID saleId, PointOfSaleProduct product, int quantity, BigDecimal subtotal) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.pos_sale_line (
				    tenant_id, sale_id, product_id, product_sku_snapshot, product_title_snapshot,
				    inventory_managed, quantity, unit_price_pen, subtotal_pen
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id, product_id, product_sku_snapshot, product_title_snapshot, inventory_managed,
				          quantity, unit_price_pen, subtotal_pen
				""", this::mapSaleLine, tenantId, saleId, product.id(), product.sku(), product.title(), product.inventoryManaged(),
				quantity, product.salePricePen(), subtotal);
	}

	public List<PosSaleLine> listLines(UUID saleId) {
		return jdbcTemplate.query("""
				select id, product_id, product_sku_snapshot, product_title_snapshot, inventory_managed,
				       quantity, unit_price_pen, subtotal_pen
				from inventory_sales.pos_sale_line where sale_id = ? order by id
				""", this::mapSaleLine, saleId);
	}

	public Optional<StockBalanceRow> lockBalance(UUID productId) {
		return jdbcTemplate.query("""
				select id, available_quantity, version
				from inventory_sales.stock_balance where product_id = ? for update
				""", (resultSet, rowNumber) -> new StockBalanceRow(resultSet.getObject("id", UUID.class),
						resultSet.getInt("available_quantity"), resultSet.getInt("version")), productId).stream().findFirst();
	}

	public void updateBalance(StockBalanceRow current, int quantityAfter) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.stock_balance
				set available_quantity = ?, version = version + 1, updated_at = statement_timestamp()
				where id = ? and version = ?
				""", quantityAfter, current.id(), current.version());
		if (changed != 1) {
			throw new IllegalStateException("El saldo cambio durante una operacion serializada.");
		}
	}

	public void insertSaleMovement(UUID tenantId, PosSale sale, PosSaleLine line, int quantityBefore, int quantityAfter,
			UUID movementOperationId, UUID actorPrincipalId) {
		jdbcTemplate.update("""
				insert into inventory_sales.stock_movement (
				    tenant_id, product_id, movement_type, quantity_delta, quantity_before, quantity_after,
				    operation_id, reason, actor_principal_id, pos_sale_id
				) values (?, ?, 'VENTA_POS', ?, ?, ?, ?, ?, ?, ?)
				""", tenantId, line.productId(), -line.quantity(), quantityBefore, quantityAfter, movementOperationId,
				"Venta presencial " + sale.id(), actorPrincipalId, sale.id());
	}

	public PosManualPayment insertValidatedPayment(UUID tenantId, UUID saleId, String paymentMethod, BigDecimal amount,
			UUID cashierPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.pos_manual_payment (
				    tenant_id, sale_id, payment_method, declared_amount_pen, status,
				    declared_by_principal_id, validated_by_principal_id, validated_at
				) values (?, ?, ?, ?, 'VALIDADO', ?, ?, statement_timestamp())
				returning id, payment_method, declared_amount_pen, status, declared_by_principal_id,
				          validated_by_principal_id, declared_at, validated_at
				""", this::mapPayment, tenantId, saleId, paymentMethod, amount, cashierPrincipalId, cashierPrincipalId);
	}

	public Optional<PosManualPayment> findPayment(UUID saleId) {
		return jdbcTemplate.query("""
				select id, payment_method, declared_amount_pen, status, declared_by_principal_id,
				       validated_by_principal_id, declared_at, validated_at
				from inventory_sales.pos_manual_payment where sale_id = ?
				""", this::mapPayment, saleId).stream().findFirst();
	}

	public PosSale completeSale(UUID saleId) {
		return jdbcTemplate.queryForObject("""
				update inventory_sales.pos_sale
				set status = 'COMPLETADA', completed_at = statement_timestamp(), updated_at = statement_timestamp()
				where id = ? and status = 'PREPARACION'
				returning id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				          created_at, completed_at, updated_at
				""", this::mapSale, saleId);
	}

	public PosSale updateSaleStatus(UUID saleId, String status) {
		return jdbcTemplate.queryForObject("""
				update inventory_sales.pos_sale
				set status = ?, updated_at = statement_timestamp()
				where id = ?
				returning id, tenant_id, cashier_principal_id, status, operation_id, total_amount_pen,
				          created_at, completed_at, updated_at
				""", this::mapSale, status, saleId);
	}

	public Optional<PosSaleCancellation> findCancellation(UUID saleId) {
		return jdbcTemplate.query("""
				select id, sale_id, operation_id, reason, items_left_premises, stock_restocked,
				       cancelled_by_principal_id, created_at
				from inventory_sales.pos_sale_cancellation where sale_id = ?
				""", this::mapCancellation, saleId).stream().findFirst();
	}

	public Optional<PosSaleCancellation> findCancellationByOperation(UUID operationId) {
		return jdbcTemplate.query("""
				select id, sale_id, operation_id, reason, items_left_premises, stock_restocked,
				       cancelled_by_principal_id, created_at
				from inventory_sales.pos_sale_cancellation where operation_id = ?
				""", this::mapCancellation, operationId).stream().findFirst();
	}

	public PosSaleCancellation insertCancellation(UUID tenantId, UUID saleId, UUID operationId, String reason,
			boolean itemsLeftPremises, UUID actorPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.pos_sale_cancellation (
				    tenant_id, sale_id, operation_id, reason, items_left_premises, stock_restocked, cancelled_by_principal_id
				) values (?, ?, ?, ?, ?, ?, ?)
				returning id, sale_id, operation_id, reason, items_left_premises, stock_restocked,
				          cancelled_by_principal_id, created_at
				""", this::mapCancellation, tenantId, saleId, operationId, reason, itemsLeftPremises, !itemsLeftPremises,
				actorPrincipalId);
	}

	public Optional<PosReturn> findReturn(UUID returnId) {
		return jdbcTemplate.query(returnQuery("where id = ?"), this::mapReturn, returnId).stream().findFirst();
	}

	public Optional<PosReturn> findReturnByOperation(UUID operationId) {
		return jdbcTemplate.query(returnQuery("where operation_id = ?"), this::mapReturn, operationId).stream().findFirst();
	}

	public Optional<PosReturn> lockReturn(UUID returnId) {
		return jdbcTemplate.query(returnQuery("where id = ? for update"), this::mapReturn, returnId).stream().findFirst();
	}

	public List<PosReturn> listReturns(UUID saleId) {
		return jdbcTemplate.query(returnQuery("where cast(? as uuid) is null or sale_id = cast(? as uuid) order by created_at desc, id desc"),
				this::mapReturn, saleId, saleId);
	}

	public boolean hasReturns(UUID saleId) {
		Boolean value = jdbcTemplate.queryForObject("select exists (select 1 from inventory_sales.pos_return where sale_id = ?)",
				Boolean.class, saleId);
		return Boolean.TRUE.equals(value);
	}

	public PosReturn insertReturn(UUID tenantId, UUID saleId, UUID operationId, String reason, UUID requestedByPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into inventory_sales.pos_return (tenant_id, sale_id, operation_id, reason, requested_by_principal_id)
				values (?, ?, ?, ?, ?)
				returning id, tenant_id, sale_id, operation_id, status, reason, requested_by_principal_id,
				          received_by_principal_id, inspected_by_principal_id, closed_by_principal_id,
				          created_at, received_at, inspected_at, closed_at, updated_at
				""", this::mapReturn, tenantId, saleId, operationId, reason, requestedByPrincipalId);
	}

	public void insertReturnLine(UUID tenantId, UUID returnId, UUID saleLineId, int requestedQuantity) {
		jdbcTemplate.update("""
				insert into inventory_sales.pos_return_line (tenant_id, return_id, sale_line_id, requested_quantity)
				values (?, ?, ?, ?)
				""", tenantId, returnId, saleLineId, requestedQuantity);
	}

	public List<PosReturnLine> listReturnLines(UUID returnId) {
		return jdbcTemplate.query(returnLineQuery("where return_line.return_id = ? order by sale_line.product_id, return_line.id"),
				this::mapReturnLine, returnId);
	}

	public List<PosReturnLine> lockReturnLines(UUID returnId) {
		return jdbcTemplate.query(returnLineQuery("where return_line.return_id = ? order by sale_line.product_id, return_line.id for update of return_line"),
				this::mapReturnLine, returnId);
	}

	public int allocatedReturnQuantity(UUID saleLineId) {
		Integer value = jdbcTemplate.queryForObject("""
				select coalesce(sum(return_line.requested_quantity), 0)
				from inventory_sales.pos_return_line return_line
				where return_line.sale_line_id = ?
				""", Integer.class, saleLineId);
		return value == null ? 0 : value;
	}

	public void markReturnReceived(UUID returnId, UUID actorPrincipalId) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.pos_return
				set status = 'RECIBIDA', received_by_principal_id = ?, received_at = statement_timestamp(),
				    updated_at = statement_timestamp()
				where id = ? and status = 'SOLICITADA'
				""", actorPrincipalId, returnId);
		if (changed != 1) throw new IllegalStateException("La devolución cambió durante la recepción.");
	}

	public void updateReturnLineReceipt(UUID returnLineId, int receivedQuantity) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.pos_return_line
				set received_quantity = ?
				where id = ? and inspection_result = 'PENDIENTE'
				""", receivedQuantity, returnLineId);
		if (changed != 1) throw new IllegalStateException("La línea de devolución cambió durante la recepción.");
	}

	public void markReturnInspected(UUID returnId, String status, UUID actorPrincipalId) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.pos_return
				set status = ?, inspected_by_principal_id = ?, inspected_at = statement_timestamp(),
				    updated_at = statement_timestamp()
				where id = ? and status = 'RECIBIDA'
				""", status, actorPrincipalId, returnId);
		if (changed != 1) throw new IllegalStateException("La devolución cambió durante la inspección.");
	}

	public void updateReturnLineInspection(UUID returnLineId, int restockedQuantity, String inspectionResult) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.pos_return_line
				set restocked_quantity = ?, inspection_result = ?
				where id = ? and inspection_result = 'PENDIENTE'
				""", restockedQuantity, inspectionResult, returnLineId);
		if (changed != 1) throw new IllegalStateException("La línea de devolución cambió durante la inspección.");
	}

	public void closeReturn(UUID returnId, UUID actorPrincipalId) {
		int changed = jdbcTemplate.update("""
				update inventory_sales.pos_return
				set status = 'CERRADA', closed_by_principal_id = ?, closed_at = statement_timestamp(),
				    updated_at = statement_timestamp()
				where id = ? and status in ('INSPECCIONADA_APTA', 'INSPECCIONADA_NO_APTA')
				""", actorPrincipalId, returnId);
		if (changed != 1) throw new IllegalStateException("La devolución cambió durante el cierre.");
	}

	public boolean allInventoryLinesReturned(UUID saleId) {
		Boolean complete = jdbcTemplate.queryForObject("""
				select not exists (
				    select 1
				    from inventory_sales.pos_sale_line sale_line
				    where sale_line.sale_id = ? and sale_line.inventory_managed
				      and sale_line.quantity > coalesce((
				          select sum(return_line.received_quantity)
				          from inventory_sales.pos_return_line return_line
				          join inventory_sales.pos_return pos_return
				            on pos_return.tenant_id = return_line.tenant_id and pos_return.id = return_line.return_id
				          where return_line.sale_line_id = sale_line.id
				            and pos_return.status in ('INSPECCIONADA_APTA', 'INSPECCIONADA_NO_APTA', 'CERRADA')
				      ), 0)
				)
				""", Boolean.class, saleId);
		return Boolean.TRUE.equals(complete);
	}

	public void insertReturnMovement(UUID tenantId, PosSale sale, PosReturn posReturn, PosReturnLine line,
			int quantityBefore, int quantityAfter, UUID movementOperationId, String reason, UUID actorPrincipalId) {
		jdbcTemplate.update("""
				insert into inventory_sales.stock_movement (
				    tenant_id, product_id, movement_type, quantity_delta, quantity_before, quantity_after,
				    operation_id, reason, actor_principal_id, pos_sale_id, pos_return_id
				) values (?, ?, 'DEVOLUCION_APTA', ?, ?, ?, ?, ?, ?, ?, ?)
				""", tenantId, line.productId(), quantityAfter - quantityBefore, quantityBefore, quantityAfter,
				movementOperationId, reason, actorPrincipalId, sale.id(), posReturn == null ? null : posReturn.id());
	}

	private String returnQuery(String whereClause) {
		return """
				select id, tenant_id, sale_id, operation_id, status, reason, requested_by_principal_id,
				       received_by_principal_id, inspected_by_principal_id, closed_by_principal_id,
				       created_at, received_at, inspected_at, closed_at, updated_at
				from inventory_sales.pos_return
				""" + whereClause;
	}

	private String returnLineQuery(String whereClause) {
		return """
				select return_line.id, return_line.sale_line_id, sale_line.product_id, sale_line.product_sku_snapshot,
				       sale_line.product_title_snapshot, return_line.requested_quantity, return_line.received_quantity,
				       return_line.restocked_quantity, return_line.inspection_result
				from inventory_sales.pos_return_line return_line
				join inventory_sales.pos_sale_line sale_line
				  on sale_line.tenant_id = return_line.tenant_id and sale_line.id = return_line.sale_line_id
				""" + whereClause;
	}

	private PointOfSaleProduct mapSaleableProduct(ResultSet resultSet, int rowNumber) throws SQLException {
		Integer availability = resultSet.getObject("available_quantity", Integer.class);
		return new PointOfSaleProduct(resultSet.getObject("id", UUID.class), resultSet.getString("sku"),
				resultSet.getString("title"), resultSet.getBigDecimal("sale_price_pen"), resultSet.getString("status"),
				resultSet.getBoolean("inventory_managed"), availability);
	}

	private PosSale mapSale(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosSale(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("cashier_principal_id", UUID.class), resultSet.getString("status"),
				resultSet.getObject("operation_id", UUID.class), resultSet.getBigDecimal("total_amount_pen"),
				instant(resultSet, "created_at"), instant(resultSet, "completed_at"), instant(resultSet, "updated_at"), List.of(), null);
	}

	private PosSaleLine mapSaleLine(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosSaleLine(resultSet.getObject("id", UUID.class), resultSet.getObject("product_id", UUID.class),
				resultSet.getString("product_sku_snapshot"), resultSet.getString("product_title_snapshot"),
				resultSet.getBoolean("inventory_managed"), resultSet.getInt("quantity"),
				resultSet.getBigDecimal("unit_price_pen"), resultSet.getBigDecimal("subtotal_pen"));
	}

	private PosManualPayment mapPayment(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosManualPayment(resultSet.getObject("id", UUID.class), resultSet.getString("payment_method"),
				resultSet.getBigDecimal("declared_amount_pen"), resultSet.getString("status"),
				resultSet.getObject("declared_by_principal_id", UUID.class), resultSet.getObject("validated_by_principal_id", UUID.class),
				instant(resultSet, "declared_at"), instant(resultSet, "validated_at"));
	}

	private PosSaleCancellation mapCancellation(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosSaleCancellation(resultSet.getObject("id", UUID.class), resultSet.getObject("sale_id", UUID.class),
				resultSet.getObject("operation_id", UUID.class), resultSet.getString("reason"),
				resultSet.getBoolean("items_left_premises"), resultSet.getBoolean("stock_restocked"),
				resultSet.getObject("cancelled_by_principal_id", UUID.class), instant(resultSet, "created_at"));
	}

	private PosReturn mapReturn(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosReturn(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("sale_id", UUID.class), resultSet.getObject("operation_id", UUID.class),
				resultSet.getString("status"), resultSet.getString("reason"),
				resultSet.getObject("requested_by_principal_id", UUID.class), resultSet.getObject("received_by_principal_id", UUID.class),
				resultSet.getObject("inspected_by_principal_id", UUID.class), resultSet.getObject("closed_by_principal_id", UUID.class),
				instant(resultSet, "created_at"), instant(resultSet, "received_at"), instant(resultSet, "inspected_at"),
				instant(resultSet, "closed_at"), instant(resultSet, "updated_at"), List.of());
	}

	private PosReturnLine mapReturnLine(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PosReturnLine(resultSet.getObject("id", UUID.class), resultSet.getObject("sale_line_id", UUID.class),
				resultSet.getObject("product_id", UUID.class), resultSet.getString("product_sku_snapshot"),
				resultSet.getString("product_title_snapshot"), resultSet.getInt("requested_quantity"),
				resultSet.getInt("received_quantity"), resultSet.getInt("restocked_quantity"),
				resultSet.getString("inspection_result"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	public record StockBalanceRow(UUID id, int availableQuantity, int version) {
	}
}
