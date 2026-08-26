package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.util.UUID

@Service
class TransferExecutionService(
    private val jdbc: JdbcTemplate,
    private val fleetbaseOrderLinks: FleetbaseOrderLinkService
) {
    private val mapper = RowMapper { rs: ResultSet, _: Int -> rs.toExecution() }

    @Transactional
    fun create(actor: TenantAccessContext, proposalId: UUID, key: String, body: CreateTransferExecutionRequest): TransferExecutionDetail {
        if (key.isBlank() || key.length > 160) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required")
        byProposal(actor, proposalId)?.let { return detail(actor, it.executionId) }
        val proposal = jdbc.query("SELECT * FROM action_proposal WHERE tenant_id=? AND proposal_id=? FOR UPDATE", { rs, _ -> mapProposal(rs) }, actor.tenantId, proposalId).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer proposal was not found")
        if (proposal.type != "TRANSFER") throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only transfer proposals can be executed")
        if (proposal.status != "APPROVED") throw ResponseStatusException(HttpStatus.CONFLICT, "The transfer proposal must be APPROVED before execution")
        requireWarehouse(actor, proposal.source)
        requireWarehouse(actor, proposal.destination)
        val quantity = try { proposal.quantity.longValueExact() } catch (_: ArithmeticException) { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Execution quantity must be a whole number of units") }
        val id = UUID.randomUUID()
        jdbc.update("""INSERT INTO transfer_execution(execution_id,tenant_id,proposal_id,status,sku_id,source_warehouse_id,destination_warehouse_id,quantity,route_reference,vehicle_reference,idempotency_key,created_by)
            VALUES (?, ?, ?, 'PLANNED', ?, ?, ?, ?, ?, ?, ?, ?)""", id, actor.tenantId, proposalId, proposal.sku, proposal.source, proposal.destination, quantity, body.routeReference, body.vehicleReference, key, actor.userId)
        event(id, actor, null, "PLANNED", body.comment ?: "Execution created from approved proposal")
        return detail(actor, id)
    }

    fun list(actor: TenantAccessContext): List<TransferExecutionView> {
        val args = mutableListOf<Any>(actor.tenantId)
        val sql = StringBuilder("SELECT * FROM transfer_execution WHERE tenant_id=?")
        if (actor.warehouseIds.isNotEmpty()) {
            val p = actor.warehouseIds.joinToString(",") { "?" }
            sql.append(" AND (source_warehouse_id IN ($p) OR destination_warehouse_id IN ($p))")
            args.addAll(actor.warehouseIds); args.addAll(actor.warehouseIds)
        }
        sql.append(" ORDER BY updated_at DESC LIMIT 200")
        return jdbc.query(sql.toString(), mapper, *args.toTypedArray())
    }

    fun detail(actor: TenantAccessContext, id: UUID): TransferExecutionDetail {
        val execution = requireExecution(actor, id)
        val allocations = jdbc.query("SELECT batch_number,quantity,expiry_date,unit_cost,currency FROM transfer_execution_allocation WHERE execution_id=? ORDER BY expiry_date", { rs, _ -> TransferAllocationView(rs.getString(1), rs.getLong(2), rs.getDate(3).toString(), rs.getBigDecimal(4), rs.getString(5)) }, id)
        val events = jdbc.query("SELECT event_id,from_status,to_status,changed_by,comment,occurred_at FROM transfer_execution_event WHERE execution_id=? ORDER BY occurred_at", { rs, _ -> TransferExecutionEventView(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toLocalDateTime()) }, id)
        return TransferExecutionDetail(execution, allocations, events, fleetbaseOrderLinks.findForExecution(actor, id))
    }

    @Transactional
    fun reserve(actor: TenantAccessContext, id: UUID, comment: String?): TransferExecutionDetail {
        val execution = lockExecution(actor, id)
        requireStatus(execution, "PLANNED")
        val batches = jdbc.query("""SELECT * FROM batch_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=?
            AND snapshot_date=(SELECT MAX(snapshot_date) FROM batch_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=?)
            AND available_quantity-reserved_quantity-blocked_quantity>0 ORDER BY expiry_date,batch_number FOR UPDATE""",
            { rs, _ -> BatchRow(UUID.fromString(rs.getString("batch_inventory_id")), rs.getString("batch_number"), rs.getLong("available_quantity")-rs.getLong("reserved_quantity")-rs.getLong("blocked_quantity"), rs.getDate("expiry_date"), rs.getDate("manufacture_date"), rs.getBigDecimal("unit_cost"), rs.getString("currency"), rs.getString("storage_condition_code")) },
            actor.tenantId, execution.sourceWarehouseId, execution.skuId, actor.tenantId, execution.sourceWarehouseId, execution.skuId)
        if (batches.sumOf { it.usable } < execution.quantity) throw ResponseStatusException(HttpStatus.CONFLICT, "Insufficient usable source stock to reserve ${execution.quantity} units")
        var remaining = execution.quantity
        batches.forEach { batch ->
            if (remaining <= 0) return@forEach
            val allocate = minOf(remaining, batch.usable)
            val updated = jdbc.update("UPDATE batch_inventory SET reserved_quantity=reserved_quantity+?,updated_at=CURRENT_TIMESTAMP WHERE batch_inventory_id=? AND available_quantity-reserved_quantity-blocked_quantity>=?", allocate, batch.id, allocate)
            if (updated != 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Inventory changed during reservation; retry")
            jdbc.update("""INSERT INTO transfer_execution_allocation(allocation_id,execution_id,tenant_id,source_batch_inventory_id,batch_number,quantity,expiry_date,manufacture_date,unit_cost,currency,storage_condition_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), id, actor.tenantId, batch.id, batch.number, allocate, batch.expiry, batch.manufacture, batch.cost, batch.currency, batch.storage)
            remaining -= allocate
        }
        transition(id, actor, "PLANNED", "RESERVED", "reserved_at", comment ?: "FEFO stock reserved")
        return detail(actor, id)
    }

    @Transactional
    fun dispatch(actor: TenantAccessContext, id: UUID, comment: String?): TransferExecutionDetail {
        val execution = lockExecution(actor, id); requireStatus(execution, "RESERVED")
        val allocations = jdbc.query("SELECT source_batch_inventory_id,quantity FROM transfer_execution_allocation WHERE execution_id=? FOR UPDATE", { rs, _ -> UUID.fromString(rs.getString(1)) to rs.getLong(2) }, id)
        fleetbaseOrderLinks.dispatchRemoteOrderIfLinked(actor, id)
        allocations.forEach { (batchId, qty) ->
            val updated = jdbc.update("""UPDATE batch_inventory SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity-?,last_movement_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE batch_inventory_id=? AND reserved_quantity>=? AND available_quantity>=?""", qty, qty, batchId, qty, qty)
            if (updated != 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Reserved source stock is no longer consistent")
        }
        val changed = jdbc.update("UPDATE transfer_execution SET status='IN_TRANSIT',dispatched_by=?,dispatched_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE execution_id=? AND status='RESERVED' AND version=?", actor.userId, id, execution.version)
        if (changed != 1) conflict()
        event(id, actor, "RESERVED", "IN_TRANSIT", comment ?: "Shipment dispatched; source stock consumed")
        return detail(actor, id)
    }

    @Transactional
    fun receive(actor: TenantAccessContext, id: UUID, body: ReceiveTransferRequest): TransferExecutionDetail {
        val execution = lockExecution(actor, id); requireStatus(execution, "IN_TRANSIT")
        val snapshot = jdbc.queryForObject("SELECT MAX(snapshot_date) FROM batch_inventory WHERE tenant_id=?", java.sql.Date::class.java, actor.tenantId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No inventory snapshot exists for this tenant")
        val allocations = jdbc.query("SELECT * FROM transfer_execution_allocation WHERE execution_id=? ORDER BY expiry_date FOR UPDATE", { rs, _ -> ReceiptRow(rs.getString("batch_number"), rs.getLong("quantity"), rs.getDate("expiry_date"), rs.getDate("manufacture_date"), rs.getBigDecimal("unit_cost"), rs.getString("currency"), rs.getString("storage_condition_code")) }, id)
        allocations.forEach { row ->
            val updated = jdbc.update("""UPDATE batch_inventory SET available_quantity=available_quantity+?,last_movement_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND snapshot_date=? AND warehouse_id=? AND sku_id=? AND batch_number=?""", row.quantity, actor.tenantId, snapshot, execution.destinationWarehouseId, execution.skuId, row.number)
            if (updated == 0) jdbc.update("""INSERT INTO batch_inventory(batch_inventory_id,snapshot_date,tenant_id,warehouse_id,sku_id,batch_number,manufacture_date,expiry_date,available_quantity,reserved_quantity,blocked_quantity,unit_cost,currency,storage_condition_code,last_movement_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, CURRENT_TIMESTAMP)""", UUID.randomUUID(), snapshot, actor.tenantId, execution.destinationWarehouseId, execution.skuId, row.number, row.manufacture, row.expiry, row.quantity, row.cost, row.currency, row.storage)
        }
        val changed = jdbc.update("""UPDATE transfer_execution SET status='RECEIVED',received_by=?,received_at=CURRENT_TIMESTAMP,actual_transport_cost=?,actual_carbon_kg=?,updated_at=CURRENT_TIMESTAMP,version=version+1
            WHERE execution_id=? AND status='IN_TRANSIT' AND version=?""", actor.userId, body.actualTransportCost, body.actualCarbonKg, id, execution.version)
        if (changed != 1) conflict()
        event(id, actor, "IN_TRANSIT", "RECEIVED", body.comment ?: "Destination receipt confirmed; inventory posted")
        return detail(actor, id)
    }

    @Transactional
    fun cancel(actor: TenantAccessContext, id: UUID, comment: String?): TransferExecutionDetail {
        val execution = lockExecution(actor, id)
        if (execution.status !in setOf("PLANNED", "RESERVED")) throw ResponseStatusException(HttpStatus.CONFLICT, "Only planned or reserved executions can be cancelled")
        if (execution.status == "RESERVED") jdbc.query("SELECT source_batch_inventory_id,quantity FROM transfer_execution_allocation WHERE execution_id=? FOR UPDATE", { rs, _ -> UUID.fromString(rs.getString(1)) to rs.getLong(2) }, id).forEach { (batch, qty) -> jdbc.update("UPDATE batch_inventory SET reserved_quantity=reserved_quantity-?,updated_at=CURRENT_TIMESTAMP WHERE batch_inventory_id=? AND reserved_quantity>=?", qty, batch, qty) }
        jdbc.update("UPDATE transfer_execution SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE execution_id=? AND version=?", id, execution.version)
        jdbc.update("UPDATE fleetbase_order_link SET link_status='CANCELLED',updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND transfer_execution_id=? AND link_status='PREPARED'", actor.tenantId, id)
        event(id, actor, execution.status, "CANCELLED", comment ?: "Execution cancelled")
        return detail(actor, id)
    }

    private fun transition(id: UUID, actor: TenantAccessContext, from: String, to: String, timestamp: String, comment: String) {
        val changed = jdbc.update("UPDATE transfer_execution SET status=?,${timestamp}=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE execution_id=? AND status=?", to, id, from)
        if (changed != 1) conflict(); event(id, actor, from, to, comment)
    }
    private fun event(id: UUID, actor: TenantAccessContext, from: String?, to: String, comment: String?) = jdbc.update("INSERT INTO transfer_execution_event(event_id,execution_id,tenant_id,from_status,to_status,changed_by,comment) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), id, actor.tenantId, from, to, actor.userId, comment)
    private fun requireStatus(e: TransferExecutionView, status: String) { if (e.status != status) throw ResponseStatusException(HttpStatus.CONFLICT, "Execution must be $status, but is ${e.status}") }
    private fun conflict(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Execution changed concurrently; reload and retry")
    private fun requireWarehouse(actor: TenantAccessContext, id: String) { if (actor.warehouseIds.isNotEmpty() && id !in actor.warehouseIds) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Caller is not authorised for warehouse '$id'") }
    private fun byProposal(actor: TenantAccessContext, proposal: UUID) = jdbc.query("SELECT * FROM transfer_execution WHERE tenant_id=? AND proposal_id=?", mapper, actor.tenantId, proposal).firstOrNull()
    private fun requireExecution(actor: TenantAccessContext, id: UUID): TransferExecutionView { val e = jdbc.query("SELECT * FROM transfer_execution WHERE tenant_id=? AND execution_id=?", mapper, actor.tenantId, id).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer execution was not found"); requireWarehouse(actor,e.sourceWarehouseId); requireWarehouse(actor,e.destinationWarehouseId); return e }
    private fun lockExecution(actor: TenantAccessContext, id: UUID): TransferExecutionView { val e = jdbc.query("SELECT * FROM transfer_execution WHERE tenant_id=? AND execution_id=? FOR UPDATE", mapper, actor.tenantId, id).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer execution was not found"); requireWarehouse(actor,e.sourceWarehouseId); requireWarehouse(actor,e.destinationWarehouseId); return e }
    private fun ResultSet.toExecution() = TransferExecutionView(UUID.fromString(getString("execution_id")),UUID.fromString(getString("proposal_id")),getString("tenant_id"),getString("status"),getString("sku_id"),getString("source_warehouse_id"),getString("destination_warehouse_id"),getLong("quantity"),getString("route_reference"),getString("vehicle_reference"),getBigDecimal("actual_transport_cost"),getBigDecimal("actual_carbon_kg"),getString("created_by"),getString("dispatched_by"),getString("received_by"),getTimestamp("created_at").toLocalDateTime(),getTimestamp("reserved_at")?.toLocalDateTime(),getTimestamp("dispatched_at")?.toLocalDateTime(),getTimestamp("received_at")?.toLocalDateTime(),getTimestamp("updated_at").toLocalDateTime(),getLong("version"))
    private fun mapProposal(rs: ResultSet) = ProposalRow(rs.getString("proposal_type"),rs.getString("status"),rs.getString("sku_id"),rs.getBigDecimal("quantity"),rs.getString("source_warehouse_id"),rs.getString("destination_warehouse_id"))
    private data class ProposalRow(val type:String,val status:String,val sku:String,val quantity:java.math.BigDecimal,val source:String,val destination:String)
    private data class BatchRow(val id:UUID,val number:String,val usable:Long,val expiry:java.sql.Date,val manufacture:java.sql.Date?,val cost:java.math.BigDecimal,val currency:String,val storage:String)
    private data class ReceiptRow(val number:String,val quantity:Long,val expiry:java.sql.Date,val manufacture:java.sql.Date?,val cost:java.math.BigDecimal,val currency:String,val storage:String)
}
