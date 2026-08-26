package com.stockflow.replenishment

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Service
class ReplenishmentService(private val jdbc: JdbcTemplate) {
    fun plans(actor: TenantAccessContext, targetCoverDays: Int): ReplenishmentSummaryView {
        if (targetCoverDays !in 7..90) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCoverDays must be between 7 and 90")
        val rows = jdbc.query(SQL, { rs, _ -> RawPlan(
            asOfDate = rs.getDate("as_of_date").toLocalDate(), warehouseId = rs.getString("warehouse_id"), warehouseName = rs.getString("warehouse_name"),
            skuId = rs.getString("sku_id"), skuName = rs.getString("sku_name"), safetyStock = rs.getLong("minimum_safety_stock"),
            reorderMultiple = rs.getLong("reorder_multiple"), skuUnitCost = rs.getBigDecimal("sku_unit_cost"), usable = rs.getLong("usable_quantity"),
            salesDaily = rs.getBigDecimal("sales_daily") ?: BigDecimal.ZERO, forecastDaily = rs.getBigDecimal("forecast_daily"),
            forecastConfidence = rs.getString("forecast_confidence"), supplierId = rs.getString("supplier_id"), supplierName = rs.getString("supplier_name"),
            leadTimeDays = rs.getInt("lead_time_days"), supplierCost = rs.getBigDecimal("supplier_unit_cost"), openPurchase = rs.getLong("open_purchase_quantity"),
            proposalStatus = rs.getString("proposal_status")
        ) }, actor.tenantId, actor.tenantId, actor.tenantId, actor.tenantId, actor.tenantId, actor.tenantId, actor.tenantId)

        val visible = if (actor.warehouseIds.isEmpty()) rows else rows.filter { it.warehouseId in actor.warehouseIds }
        val calculated = visible.mapNotNull { calculate(it, targetCoverDays) }
            .sortedWith(compareBy<ReplenishmentPlanView> { riskRank(it.risk) }.thenBy { it.needBy }.thenByDescending { it.plannedValue })
        return ReplenishmentSummaryView(calculated.firstOrNull()?.asOfDate, targetCoverDays, calculated.size,
            calculated.count { it.risk == "CRITICAL" }, calculated.fold(BigDecimal.ZERO) { total, it -> total + it.plannedValue },
            calculated.sumOf { it.openPurchaseQuantity }, calculated)
    }

    private fun calculate(row: RawPlan, targetCoverDays: Int): ReplenishmentPlanView? {
        val forecastUsable = row.forecastDaily != null && row.forecastDaily > BigDecimal.ZERO
        val daily = (row.forecastDaily?.takeIf { it > BigDecimal.ZERO } ?: row.salesDaily).setScale(4, RoundingMode.HALF_UP)
        if (daily <= BigDecimal.ZERO) return null
        val lead = if (row.leadTimeDays > 0) row.leadTimeDays else 7
        val target = ceil(daily.toDouble() * (lead + targetCoverDays) + row.safetyStock).toLong()
        val rawNeed = max(0L, target - row.usable - row.openPurchase)
        val rounded = if (rawNeed == 0L) 0L else ceil(rawNeed.toDouble() / row.reorderMultiple).toLong() * row.reorderMultiple
        if (rounded == 0L && row.openPurchase == 0L) return null
        val cover = BigDecimal(row.usable).divide(daily, 2, RoundingMode.HALF_UP)
        val daysUntilReorder = max(0, floor((row.usable - row.safetyStock).coerceAtLeast(0).toDouble() / daily.toDouble()).toInt() - lead)
        val risk = when { cover.toDouble() <= lead -> "CRITICAL"; cover.toDouble() <= lead + 7 -> "HIGH"; else -> "MEDIUM" }
        val confidence = when (row.forecastConfidence?.uppercase()) { "HIGH" -> 90; "MEDIUM" -> 70; "LOW" -> 45; else -> 55 }
        val cost = row.supplierCost ?: row.skuUnitCost
        val status = when { row.openPurchase > 0 -> row.proposalStatus ?: "OPEN PROPOSAL"; row.supplierId == null -> "SUPPLIER REQUIRED"; else -> "RECOMMENDED" }
        val source = if (forecastUsable) "LATEST_FORECAST" else "30_DAY_SALES"
        return ReplenishmentPlanView(
            recommendationId = "RPL-${row.warehouseId.removePrefix("WH-").take(4)}-${row.skuId.removePrefix("SKU-").take(8)}",
            warehouseId = row.warehouseId, warehouseName = row.warehouseName, skuId = row.skuId, skuName = row.skuName,
            supplierId = row.supplierId, supplierName = row.supplierName ?: "Supplier not assigned", leadTimeDays = lead,
            usableQuantity = row.usable, openPurchaseQuantity = row.openPurchase, averageDailyDemand = daily, demandSource = source,
            coverDays = cover, safetyStock = row.safetyStock, targetStock = target, reorderMultiple = row.reorderMultiple,
            recommendedQuantity = rounded, unitCost = cost, plannedValue = cost.multiply(BigDecimal(rounded)),
            needBy = row.asOfDate.plusDays(daysUntilReorder.toLong()), confidencePercent = confidence, risk = risk, status = status,
            explanation = "Target $target units covers $lead lead-time days, $targetCoverDays planning days and ${row.safetyStock} safety-stock units. ${row.usable} usable and ${row.openPurchase} open-purchase units were deducted; the result was rounded to ${row.reorderMultiple}.",
            asOfDate = row.asOfDate
        )
    }

    private fun riskRank(risk: String) = when (risk) { "CRITICAL" -> 0; "HIGH" -> 1; else -> 2 }

    private data class RawPlan(val asOfDate:LocalDate,val warehouseId:String,val warehouseName:String,val skuId:String,val skuName:String,val safetyStock:Long,val reorderMultiple:Long,val skuUnitCost:BigDecimal,val usable:Long,val salesDaily:BigDecimal,val forecastDaily:BigDecimal?,val forecastConfidence:String?,val supplierId:String?,val supplierName:String?,val leadTimeDays:Int,val supplierCost:BigDecimal?,val openPurchase:Long,val proposalStatus:String?)

    companion object { private val SQL = """
        WITH latest_inventory AS (SELECT MAX(snapshot_date) as as_of_date FROM batch_inventory WHERE tenant_id = ?),
        inventory AS (
          SELECT li.as_of_date, bi.warehouse_id, w.warehouse_name, bi.sku_id, s.sku_name, s.minimum_safety_stock, s.reorder_multiple, s.unit_cost sku_unit_cost,
                 SUM(bi.available_quantity-bi.reserved_quantity-bi.blocked_quantity) usable_quantity
          FROM latest_inventory li JOIN batch_inventory bi ON bi.snapshot_date=li.as_of_date
          JOIN warehouse w ON w.tenant_id=bi.tenant_id AND w.warehouse_id=bi.warehouse_id
          JOIN sku s ON s.tenant_id=bi.tenant_id AND s.sku_id=bi.sku_id
          WHERE bi.tenant_id=? GROUP BY li.as_of_date,bi.warehouse_id,w.warehouse_name,bi.sku_id,s.sku_name,s.minimum_safety_stock,s.reorder_multiple,s.unit_cost
        ), sales AS (
          SELECT sh.warehouse_id,sh.sku_id,SUM(sh.sales_quantity)::numeric/30 sales_daily FROM sales_history sh, latest_inventory li
          WHERE sh.tenant_id=? AND sh.sales_date BETWEEN li.as_of_date-29 AND li.as_of_date GROUP BY sh.warehouse_id,sh.sku_id
        ), forecast_agg AS (
          SELECT r.started_at,f.warehouse_id,f.sku_id,AVG(f.forecast_quantity) forecast_daily,MIN(f.confidence) forecast_confidence,
                 ROW_NUMBER() OVER(PARTITION BY f.warehouse_id,f.sku_id ORDER BY r.started_at DESC) rn
          FROM forecast_result f JOIN forecast_run r ON r.forecast_run_id=f.forecast_run_id
          WHERE f.tenant_id=? AND r.status IN ('COMPLETED','COMPLETED_WITH_ERRORS')
          GROUP BY r.started_at,f.warehouse_id,f.sku_id
        ), open_supply_rows AS (
          SELECT ap.destination_warehouse_id warehouse_id,ap.sku_id,ap.quantity::bigint open_quantity,ap.status
          FROM action_proposal ap LEFT JOIN purchase_order po ON po.tenant_id=ap.tenant_id AND po.proposal_id=ap.proposal_id
          WHERE ap.tenant_id=? AND ap.proposal_type='PURCHASE' AND ap.status IN ('DRAFT','PENDING_APPROVAL','APPROVED') AND po.purchase_order_id IS NULL
          UNION ALL
          SELECT destination_warehouse_id,sku_id,(ordered_quantity-received_quantity)::bigint,status
          FROM purchase_order WHERE tenant_id=? AND status IN ('PO_CREATED','SENT_TO_SUPPLIER','ACKNOWLEDGED','PARTIALLY_RECEIVED')
        ), open_po AS (
          SELECT warehouse_id,sku_id,SUM(open_quantity)::bigint open_purchase_quantity,MAX(status) proposal_status
          FROM open_supply_rows GROUP BY warehouse_id,sku_id
        ), preferred_supplier AS (
          SELECT DISTINCT ON (ss.sku_id) ss.sku_id,p.supplier_id,p.supplier_name,p.lead_time_days,ss.supplier_unit_cost
          FROM sku_supplier ss JOIN supplier p ON p.tenant_id=ss.tenant_id AND p.supplier_id=ss.supplier_id
          WHERE ss.tenant_id=? AND ss.active=TRUE AND p.active=TRUE ORDER BY ss.sku_id,ss.preferred DESC,p.on_time_in_full_percent DESC
        )
        SELECT i.*,COALESCE(sa.sales_daily,0) sales_daily,fa.forecast_daily,fa.forecast_confidence,ps.supplier_id,ps.supplier_name,
               COALESCE(ps.lead_time_days,7) lead_time_days,ps.supplier_unit_cost,COALESCE(op.open_purchase_quantity,0) open_purchase_quantity,op.proposal_status
        FROM inventory i LEFT JOIN sales sa USING(warehouse_id,sku_id) LEFT JOIN forecast_agg fa ON fa.warehouse_id=i.warehouse_id AND fa.sku_id=i.sku_id AND fa.rn=1
        LEFT JOIN preferred_supplier ps ON ps.sku_id=i.sku_id LEFT JOIN open_po op ON op.warehouse_id=i.warehouse_id AND op.sku_id=i.sku_id
    """.trimIndent() }
}
