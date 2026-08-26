package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Service
class PurchaseOrderService(private val jdbc: JdbcTemplate) {
    private val mapper = RowMapper { rs:ResultSet, _:Int -> rs.toOrder() }

    @Transactional
    fun create(actor:TenantAccessContext,proposalId:UUID,key:String,body:CreatePurchaseOrderRequest):PurchaseOrderDetail {
        requireKey(key)
        byProposal(actor,proposalId)?.let { return detail(actor,it.purchaseOrderId) }
        existingKey(actor.tenantId,key)?.let { throw ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key was already used for another purchase order") }
        val p=jdbc.query("SELECT * FROM action_proposal WHERE tenant_id=? AND proposal_id=? FOR UPDATE",{rs,_->ProposalRow(rs.getString("proposal_type"),rs.getString("status"),rs.getString("sku_id"),rs.getBigDecimal("quantity"),rs.getString("destination_warehouse_id"),rs.getString("supplier_reference"),rs.getBigDecimal("unit_cost"),rs.getString("currency"))},actor.tenantId,proposalId).firstOrNull()
            ?:throw ResponseStatusException(HttpStatus.NOT_FOUND,"Purchase proposal was not found")
        if(p.type!="PURCHASE") throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Only purchase proposals can create purchase orders")
        if(p.status!="APPROVED") throw ResponseStatusException(HttpStatus.CONFLICT,"The purchase proposal must be APPROVED before order creation")
        requireWarehouse(actor,p.warehouse)
        val qty=try{p.quantity.longValueExact()}catch(_:ArithmeticException){throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Purchase-order quantity must be whole units")}
        if(p.supplier.isNullOrBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"An approved purchase proposal requires a supplier reference")
        val cost=p.unitCost?:jdbc.queryForObject("SELECT unit_cost FROM sku WHERE tenant_id=? AND sku_id=?",java.math.BigDecimal::class.java,actor.tenantId,p.sku)?:java.math.BigDecimal.ZERO
        val id=UUID.randomUUID()
        jdbc.update("""INSERT INTO purchase_order(purchase_order_id,tenant_id,proposal_id,status,sku_id,destination_warehouse_id,supplier_reference,ordered_quantity,unit_cost,currency,expected_delivery_date,idempotency_key,created_by)
            VALUES(?,?,?,'PO_CREATED',?,?,?,?,?,?,?,?,?)""",id,actor.tenantId,proposalId,p.sku,p.warehouse,p.supplier,qty,cost,p.currency,body.expectedDeliveryDate,key,actor.userId)
        event(id,actor,null,"PO_CREATED",body.comment?:"Purchase order created from independently approved proposal")
        return detail(actor,id)
    }

    fun list(actor:TenantAccessContext):List<PurchaseOrderView>{
        val sql=StringBuilder("SELECT * FROM purchase_order WHERE tenant_id=?");val args=mutableListOf<Any>(actor.tenantId)
        if(actor.warehouseIds.isNotEmpty()){val marks=actor.warehouseIds.joinToString(","){"?"};sql.append(" AND destination_warehouse_id IN ($marks)");args.addAll(actor.warehouseIds)}
        sql.append(" ORDER BY updated_at DESC LIMIT 200");return jdbc.query(sql.toString(),mapper,*args.toTypedArray())
    }

    fun detail(actor:TenantAccessContext,id:UUID):PurchaseOrderDetail{
        val order=requireOrder(actor,id)
        val receipts=jdbc.query("SELECT * FROM purchase_order_receipt WHERE tenant_id=? AND purchase_order_id=? ORDER BY received_at",{rs,_->PurchaseReceiptView(UUID.fromString(rs.getString("receipt_id")),rs.getLong("quantity"),rs.getString("batch_number"),rs.getDate("manufacture_date")?.toLocalDate(),rs.getDate("expiry_date").toLocalDate(),rs.getBigDecimal("unit_cost"),rs.getString("storage_condition_code"),rs.getString("received_by"),rs.getTimestamp("received_at").toLocalDateTime())},actor.tenantId,id)
        val events=jdbc.query("SELECT * FROM purchase_order_event WHERE tenant_id=? AND purchase_order_id=? ORDER BY occurred_at",{rs,_->PurchaseOrderEventView(UUID.fromString(rs.getString("event_id")),rs.getString("from_status"),rs.getString("to_status"),rs.getString("changed_by"),rs.getString("comment"),rs.getTimestamp("occurred_at").toLocalDateTime())},actor.tenantId,id)
        return PurchaseOrderDetail(order,receipts,events)
    }

    @Transactional fun send(actor:TenantAccessContext,id:UUID,comment:String?):PurchaseOrderDetail{
        val o=lock(actor,id);transition(o,actor,"PO_CREATED","SENT_TO_SUPPLIER","sent_by",comment?:"Purchase order marked as sent to supplier");return detail(actor,id)
    }

    @Transactional fun acknowledge(actor:TenantAccessContext,id:UUID,body:AcknowledgePurchaseOrderRequest):PurchaseOrderDetail{
        val o=lock(actor,id);requireStatus(o,"SENT_TO_SUPPLIER")
        val changed=jdbc.update("""UPDATE purchase_order SET status='ACKNOWLEDGED',supplier_acknowledgement_reference=?,expected_delivery_date=COALESCE(?,expected_delivery_date),acknowledged_by=?,acknowledged_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE purchase_order_id=? AND tenant_id=? AND status='SENT_TO_SUPPLIER' AND version=?""",body.acknowledgementReference,body.expectedDeliveryDate,actor.userId,id,actor.tenantId,o.version)
        if(changed!=1) conflict();event(id,actor,"SENT_TO_SUPPLIER","ACKNOWLEDGED",body.comment?:"Supplier acknowledgement recorded");return detail(actor,id)
    }

    @Transactional fun receive(actor:TenantAccessContext,id:UUID,key:String,body:ReceivePurchaseOrderRequest):PurchaseOrderDetail{
        requireKey(key)
        val existingOrderId=jdbc.query("SELECT purchase_order_id FROM purchase_order_receipt WHERE tenant_id=? AND idempotency_key=?",{rs,_->UUID.fromString(rs.getString("purchase_order_id"))},actor.tenantId,key).firstOrNull()
        if(existingOrderId!=null){
            if(existingOrderId!=id) throw ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key was already used for another purchase order receipt")
            return detail(actor,id)
        }
        val o=lock(actor,id)
        if(o.status !in setOf("ACKNOWLEDGED","PARTIALLY_RECEIVED")) throw ResponseStatusException(HttpStatus.CONFLICT,"Purchase order must be ACKNOWLEDGED or PARTIALLY_RECEIVED before receipt")
        if(body.manufactureDate!=null && !body.expiryDate.isAfter(body.manufactureDate)) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Expiry date must be after manufacture date")
        if(!body.expiryDate.isAfter(LocalDate.now())) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Expired stock cannot be received")
        if(body.quantity>o.remainingQuantity) throw ResponseStatusException(HttpStatus.CONFLICT,"Receipt quantity exceeds ${o.remainingQuantity} remaining units")
        val receiptId=UUID.randomUUID();val cost=body.unitCost?:o.unitCost
        jdbc.update("""INSERT INTO purchase_order_receipt(receipt_id,purchase_order_id,tenant_id,idempotency_key,quantity,batch_number,manufacture_date,expiry_date,unit_cost,storage_condition_code,received_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)""",receiptId,id,actor.tenantId,key,body.quantity,body.batchNumber.trim(),body.manufactureDate,body.expiryDate,cost,body.storageConditionCode.uppercase(),actor.userId)
        postInventory(actor,o,body,cost)
        val received=o.receivedQuantity+body.quantity;val target=if(received==o.orderedQuantity)"RECEIVED" else "PARTIALLY_RECEIVED"
        val changed=jdbc.update("""UPDATE purchase_order SET status=?,received_quantity=?,last_received_by=?,last_received_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE purchase_order_id=? AND tenant_id=? AND version=?""",target,received,actor.userId,id,actor.tenantId,o.version)
        if(changed!=1) conflict();event(id,actor,o.status,target,body.comment?:"${body.quantity} units received in batch ${body.batchNumber}");return detail(actor,id)
    }

    @Transactional fun cancel(actor:TenantAccessContext,id:UUID,comment:String?):PurchaseOrderDetail{
        val o=lock(actor,id)
        if(o.status !in setOf("PO_CREATED","SENT_TO_SUPPLIER","ACKNOWLEDGED")) throw ResponseStatusException(HttpStatus.CONFLICT,"Only unreceived purchase orders can be cancelled")
        if(o.receivedQuantity>0) throw ResponseStatusException(HttpStatus.CONFLICT,"A purchase order with receipts cannot be cancelled")
        val changed=jdbc.update("UPDATE purchase_order SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE purchase_order_id=? AND tenant_id=? AND version=?",id,actor.tenantId,o.version)
        if(changed!=1) conflict();event(id,actor,o.status,"CANCELLED",comment?:"Purchase order cancelled");return detail(actor,id)
    }

    private fun postInventory(actor:TenantAccessContext,o:PurchaseOrderView,b:ReceivePurchaseOrderRequest,cost:java.math.BigDecimal){
        val snapshot=jdbc.queryForObject("SELECT MAX(snapshot_date) FROM batch_inventory WHERE tenant_id=?",java.sql.Date::class.java,actor.tenantId)?:throw ResponseStatusException(HttpStatus.CONFLICT,"No inventory snapshot exists")
        val updated=jdbc.update("""UPDATE batch_inventory SET available_quantity=available_quantity+?,unit_cost=?,last_movement_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND snapshot_date=? AND warehouse_id=? AND sku_id=? AND batch_number=?""",b.quantity,cost,actor.tenantId,snapshot,o.destinationWarehouseId,o.skuId,b.batchNumber.trim())
        if(updated==0) jdbc.update("""INSERT INTO batch_inventory(batch_inventory_id,snapshot_date,tenant_id,warehouse_id,sku_id,batch_number,manufacture_date,expiry_date,available_quantity,reserved_quantity,blocked_quantity,unit_cost,currency,storage_condition_code,last_movement_at) VALUES(?,?,?,?,?,?,?,?,?,0,0,?,?,?,CURRENT_TIMESTAMP)""",UUID.randomUUID(),snapshot,actor.tenantId,o.destinationWarehouseId,o.skuId,b.batchNumber.trim(),b.manufactureDate,b.expiryDate,b.quantity,cost,o.currency,b.storageConditionCode.uppercase())
    }

    private fun transition(o:PurchaseOrderView,a:TenantAccessContext,from:String,to:String,actorColumn:String,comment:String){
        requireStatus(o,from)
        val changed=jdbc.update("UPDATE purchase_order SET status=?,$actorColumn=?,sent_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE purchase_order_id=? AND tenant_id=? AND status=? AND version=?",to,a.userId,o.purchaseOrderId,a.tenantId,from,o.version)
        if(changed!=1)conflict()
        event(o.purchaseOrderId,a,from,to,comment)
    }
    private fun event(id:UUID,a:TenantAccessContext,from:String?,to:String,comment:String?)=jdbc.update("INSERT INTO purchase_order_event(event_id,purchase_order_id,tenant_id,from_status,to_status,changed_by,comment) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),id,a.tenantId,from,to,a.userId,comment)
    private fun requireStatus(o:PurchaseOrderView,s:String){if(o.status!=s)throw ResponseStatusException(HttpStatus.CONFLICT,"Purchase order must be $s, but is ${o.status}")}
    private fun requireKey(k:String){if(k.isBlank()||k.length>160)throw ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid Idempotency-Key header is required")}
    private fun requireWarehouse(a:TenantAccessContext,w:String){if(a.warehouseIds.isNotEmpty()&&w !in a.warehouseIds)throw ResponseStatusException(HttpStatus.FORBIDDEN,"Caller is not authorised for warehouse '$w'")}
    private fun conflict():Nothing=throw ResponseStatusException(HttpStatus.CONFLICT,"Purchase order changed concurrently; reload and retry")
    private fun existingKey(t:String,k:String)=jdbc.query("SELECT * FROM purchase_order WHERE tenant_id=? AND idempotency_key=?",mapper,t,k).firstOrNull()
    private fun byProposal(a:TenantAccessContext,p:UUID)=jdbc.query("SELECT * FROM purchase_order WHERE tenant_id=? AND proposal_id=?",mapper,a.tenantId,p).firstOrNull()
    private fun requireOrder(a:TenantAccessContext,id:UUID):PurchaseOrderView{val o=jdbc.query("SELECT * FROM purchase_order WHERE tenant_id=? AND purchase_order_id=?",mapper,a.tenantId,id).firstOrNull()?:throw ResponseStatusException(HttpStatus.NOT_FOUND,"Purchase order was not found");requireWarehouse(a,o.destinationWarehouseId);return o}
    private fun lock(a:TenantAccessContext,id:UUID):PurchaseOrderView{val o=jdbc.query("SELECT * FROM purchase_order WHERE tenant_id=? AND purchase_order_id=? FOR UPDATE",mapper,a.tenantId,id).firstOrNull()?:throw ResponseStatusException(HttpStatus.NOT_FOUND,"Purchase order was not found");requireWarehouse(a,o.destinationWarehouseId);return o}
    private fun ResultSet.toOrder():PurchaseOrderView{val ordered=getLong("ordered_quantity");val received=getLong("received_quantity");return PurchaseOrderView(UUID.fromString(getString("purchase_order_id")),UUID.fromString(getString("proposal_id")),getString("tenant_id"),getString("status"),getString("sku_id"),getString("destination_warehouse_id"),getString("supplier_reference"),ordered,received,ordered-received,getBigDecimal("unit_cost"),getString("currency"),getDate("expected_delivery_date")?.toLocalDate(),getString("supplier_acknowledgement_reference"),getString("created_by"),getString("sent_by"),getString("acknowledged_by"),getString("last_received_by"),getTimestamp("created_at").toLocalDateTime(),getTimestamp("sent_at")?.toLocalDateTime(),getTimestamp("acknowledged_at")?.toLocalDateTime(),getTimestamp("last_received_at")?.toLocalDateTime(),getTimestamp("updated_at").toLocalDateTime(),getLong("version"))}
    private data class ProposalRow(val type:String,val status:String,val sku:String,val quantity:java.math.BigDecimal,val warehouse:String,val supplier:String?,val unitCost:java.math.BigDecimal?,val currency:String)
}
