package com.stockflow.orders

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID

@Service
class CustomerOrderService(private val jdbc: JdbcTemplate) {
    @Transactional
    fun create(actor: TenantAccessContext, key: String, body: CreateCustomerOrderRequest): CustomerOrderDetail {
        requireKey(key)
        existing(actor, key)?.let { return detail(actor, it.orderId) }
        requireWarehouse(actor, body.warehouseId)
        val warehouseName = jdbc.query(
            "SELECT warehouse_name FROM warehouse WHERE tenant_id=? AND warehouse_id=? AND active=TRUE",
            { rs, _ -> rs.getString("warehouse_name") }, actor.tenantId, body.warehouseId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Active warehouse was not found")
        val sku = jdbc.query(
            "SELECT sku_name,selling_price,currency FROM sku WHERE tenant_id=? AND sku_id=?",
            { rs, _ -> Triple(rs.getString("sku_name"), rs.getBigDecimal("selling_price"), rs.getString("currency")) },
            actor.tenantId, body.skuId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU was not found in this tenant")
        val unitPrice = body.unitPrice ?: sku.second
        val value = unitPrice.multiply(BigDecimal(body.quantity))
        val id = UUID.randomUUID()
        val number = "SO-${id.toString().take(8).uppercase()}"
        jdbc.update(
            """INSERT INTO customer_order(order_id,tenant_id,order_number,customer_name,customer_city,channel,warehouse_id,status,promised_at,fulfilment_percent,total_value,currency,idempotency_key,created_by)
               VALUES(?,?,?,?,?,?,?,'ALLOCATED',?,0,?,?,?,?)""",
            id, actor.tenantId, number, body.customerName.trim(), body.customerCity.trim(), body.channel.trim(), body.warehouseId,
            body.promisedAt, value, sku.third, key, actor.userId
        )
        jdbc.update(
            "INSERT INTO customer_order_line(line_id,order_id,tenant_id,sku_id,ordered_quantity,unit_price,line_value) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID(), id, actor.tenantId, body.skuId, body.quantity, unitPrice, value
        )
        event(id, actor, null, "ALLOCATED", "Order created for ${sku.first} at $warehouseName")
        return detail(actor, id)
    }

    fun list(actor: TenantAccessContext): List<CustomerOrderView> {
        val sql = StringBuilder(BASE_SELECT + " WHERE co.tenant_id=?")
        val args = mutableListOf<Any>(actor.tenantId)
        if (actor.warehouseIds.isNotEmpty()) {
            sql.append(" AND co.warehouse_id IN (${actor.warehouseIds.joinToString(",") { "?" }})")
            args.addAll(actor.warehouseIds)
        }
        sql.append(" ORDER BY co.updated_at DESC LIMIT 250")
        return jdbc.query(sql.toString(), { rs, _ -> rs.toView() }, *args.toTypedArray())
    }

    fun detail(actor: TenantAccessContext, id: UUID): CustomerOrderDetail {
        val order = requireOrder(actor, id)
        val events = jdbc.query(
            "SELECT * FROM customer_order_event WHERE tenant_id=? AND order_id=? ORDER BY occurred_at",
            { rs, _ -> CustomerOrderEventView(UUID.fromString(rs.getString("event_id")), rs.getString("from_status"), rs.getString("to_status"), rs.getString("changed_by"), rs.getString("comment"), rs.getTimestamp("occurred_at").toLocalDateTime()) },
            actor.tenantId, id
        )
        return CustomerOrderDetail(order, events)
    }

    @Transactional
    fun advance(actor: TenantAccessContext, id: UUID, comment: String?): CustomerOrderDetail {
        val order = lock(actor, id)
        val target = when (order.status) {
            "ALLOCATED" -> "PICKING"
            "PICKING" -> "READY_TO_SHIP"
            "READY_TO_SHIP" -> "SHIPPED"
            "ON_HOLD" -> "ALLOCATED"
            else -> throw ResponseStatusException(HttpStatus.CONFLICT, "Order in ${order.status} cannot be advanced")
        }
        val fulfilment = when (target) {
            "PICKING" -> maxOf(order.fulfilmentPercent, 50)
            "READY_TO_SHIP", "SHIPPED" -> 100
            else -> order.fulfilmentPercent
        }
        val changed = jdbc.update(
            "UPDATE customer_order SET status=?,fulfilment_percent=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND order_id=? AND version=?",
            target, fulfilment, actor.tenantId, id, order.version
        )
        if (changed != 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Order changed concurrently; reload and retry")
        event(id, actor, order.status, target, comment ?: "Order advanced to $target")
        return detail(actor, id)
    }

    private fun existing(actor: TenantAccessContext, key: String) = jdbc.query(
        BASE_SELECT + " WHERE co.tenant_id=? AND co.idempotency_key=?", { rs, _ -> rs.toView() }, actor.tenantId, key
    ).firstOrNull()
    private fun requireOrder(actor: TenantAccessContext, id: UUID): CustomerOrderView {
        val order = jdbc.query(BASE_SELECT + " WHERE co.tenant_id=? AND co.order_id=?", { rs, _ -> rs.toView() }, actor.tenantId, id).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Customer order was not found")
        requireWarehouse(actor, order.warehouseId)
        return order
    }
    private fun lock(actor: TenantAccessContext, id: UUID): CustomerOrderView {
        val order = requireOrder(actor, id)
        jdbc.queryForObject("SELECT version FROM customer_order WHERE tenant_id=? AND order_id=? FOR UPDATE", Long::class.java, actor.tenantId, id)
        return order
    }
    private fun event(id: UUID, actor: TenantAccessContext, from: String?, to: String, comment: String?) = jdbc.update(
        "INSERT INTO customer_order_event(event_id,order_id,tenant_id,from_status,to_status,changed_by,comment) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID(), id, actor.tenantId, from, to, actor.userId, comment
    )
    private fun requireKey(key: String) {
        if (key.isBlank() || key.length > 160) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required")
    }
    private fun requireWarehouse(actor: TenantAccessContext, warehouseId: String) {
        if (actor.warehouseIds.isNotEmpty() && warehouseId !in actor.warehouseIds) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Caller is not authorised for warehouse '$warehouseId'")
    }
    private fun ResultSet.toView() = CustomerOrderView(
        UUID.fromString(getString("order_id")), getString("order_number"), getString("tenant_id"), getString("customer_name"), getString("customer_city"),
        getString("channel"), getString("warehouse_id"), getString("warehouse_name"), getString("status"), getTimestamp("promised_at").toLocalDateTime(),
        getInt("fulfilment_percent"), getBigDecimal("total_value"), getString("currency"), getInt("item_count"), getString("sku_id"), getString("sku_name"),
        getLong("ordered_quantity"), getBigDecimal("unit_price"), getString("created_by"), getTimestamp("created_at").toLocalDateTime(), getTimestamp("updated_at").toLocalDateTime(), getLong("version")
    )
    companion object {
        private const val BASE_SELECT = """SELECT co.*,w.warehouse_name,1 item_count,col.sku_id,s.sku_name,col.ordered_quantity,col.unit_price
            FROM customer_order co JOIN warehouse w ON w.tenant_id=co.tenant_id AND w.warehouse_id=co.warehouse_id
            JOIN customer_order_line col ON col.tenant_id=co.tenant_id AND col.order_id=co.order_id
            JOIN sku s ON s.tenant_id=col.tenant_id AND s.sku_id=col.sku_id"""
    }
}
