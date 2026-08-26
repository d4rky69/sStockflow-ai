package com.stockflow.replenishment

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

@Service
class TransferRecommendationService(private val jdbc: JdbcTemplate) {
    fun recommendations(actor: TenantAccessContext, targetCoverDays: Int): TransferRecommendationSummaryView {
        if (targetCoverDays !in 7..90) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCoverDays must be between 7 and 90")
        val rows = jdbc.query(SQL, { rs, _ -> Position(
            rs.getDate("as_of_date").toLocalDate(), rs.getString("warehouse_id"), rs.getString("warehouse_name"),
            rs.getBigDecimal("latitude")?.toDouble(), rs.getBigDecimal("longitude")?.toDouble(), rs.getString("sku_id"),
            rs.getString("sku_name"), rs.getLong("minimum_safety_stock"), rs.getLong("usable_quantity"),
            rs.getBigDecimal("daily_demand") ?: BigDecimal.ZERO, rs.getBigDecimal("unit_cost")
        ) }, actor.tenantId, actor.tenantId, actor.tenantId)
        val visible = if (actor.warehouseIds.isEmpty()) rows else rows.filter { it.warehouseId in actor.warehouseIds }
        val recommendations = visible.groupBy { it.skuId }.flatMap { (_, positions) ->
            val shortages = positions.mapNotNull { p ->
                val target = ceil(p.dailyDemand.toDouble() * targetCoverDays + p.safetyStock).toLong()
                (target - p.usable).takeIf { it > 0 }?.let { Need(p, it, target) }
            }
            val sources = positions.mapNotNull { p -> (p.usable - p.safetyStock).takeIf { it > 0 }?.let { Source(p, it) } }.toMutableList()
            shortages.sortedByDescending { it.quantity }.mapNotNull { need ->
                val source = sources.filter { it.position.warehouseId != need.position.warehouseId && it.surplus > 0 }
                    .minByOrNull { distanceKm(it.position, need.position) } ?: return@mapNotNull null
                val quantity = min(need.quantity, source.surplus)
                source.surplus -= quantity
                build(source.position, need, quantity)
            }
        }.sortedWith(compareBy<TransferRecommendationView> { riskRank(it.risk) }.thenByDescending { it.estimatedSavings })
        return TransferRecommendationSummaryView(recommendations.firstOrNull()?.asOfDate, recommendations.size,
            recommendations.sumOf { it.recommendedQuantity }, recommendations.sumOfDecimal { it.estimatedSavings },
            recommendations.sumOfDecimal { it.workingCapitalMoved }, recommendations.sumOfDecimal { it.estimatedCarbonKgCo2e }, recommendations)
    }

    private fun build(source: Position, need: Need, quantity: Long): TransferRecommendationView {
        val distance = distanceKm(source, need.position)
        val capacity = 6000L
        val trips = ceil(quantity.toDouble() / capacity).toInt().coerceAtLeast(1)
        val transferCost = BigDecimal(distance * 42.0 * trips).setScale(2, RoundingMode.HALF_UP)
        val purchaseCost = source.unitCost.multiply(BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP)
        val savings = purchaseCost.subtract(transferCost)
        val loadKg = min(quantity * 0.05, capacity.toDouble())
        val loadFactor = 0.5 + 0.5 * (loadKg / capacity)
        val carbon = BigDecimal(distance * 0.27 * loadFactor * trips).setScale(2, RoundingMode.HALF_UP)
        val cover = if (need.position.dailyDemand > BigDecimal.ZERO) need.position.usable / need.position.dailyDemand.toDouble() else 999.0
        val risk = if (cover <= 7) "CRITICAL" else if (cover <= 14) "HIGH" else "MEDIUM"
        return TransferRecommendationView(
            "TRN-${source.warehouseId.removePrefix("WH-").take(4)}-${need.position.warehouseId.removePrefix("WH-").take(4)}-${source.skuId.removePrefix("SKU-").take(8)}",
            source.skuId, source.skuName, source.warehouseId, source.warehouseName, need.position.warehouseId, need.position.warehouseName,
            quantity, source.usable, source.usable - quantity, source.safetyStock, need.position.usable, need.target,
            BigDecimal(distance).setScale(1, RoundingMode.HALF_UP), "diesel", capacity, trips, transferCost, purchaseCost, savings,
            source.unitCost.multiply(BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP), carbon, risk, 70,
            "Move $quantity units from ${source.warehouseName}; the source remains at ${source.usable - quantity} units, above its ${source.safetyStock}-unit safety stock, while ${need.position.warehouseName} has a ${need.quantity}-unit target gap.",
            listOf("SOURCE_SAFETY_STOCK", "DESTINATION_TARGET_STOCK", "SOURCE_AVAILABILITY", "VEHICLE_CAPACITY", "WAREHOUSE_ACCESS"),
            listOf("Geodesic distance with 1.18 road-factor", "Diesel factor 0.27 kg CO2e/km", "0.05 kg representative unit weight", "INR 42/km prototype transport rate", "Human approval required"), source.asOfDate
        )
    }

    private fun distanceKm(a: Position, b: Position): Double {
        if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) return 500.0
        val earth = 6371.0; val dLat = Math.toRadians(b.lat-a.lat); val dLon = Math.toRadians(b.lon-a.lon)
        val h = sin(dLat/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dLon/2).pow(2)
        return earth*2*atan2(sqrt(h),sqrt(1-h))*1.18
    }
    private fun riskRank(value:String)=when(value){"CRITICAL"->0;"HIGH"->1;else->2}
    private fun <T> Iterable<T>.sumOfDecimal(selector:(T)->BigDecimal)=fold(BigDecimal.ZERO){a,v->a+selector(v)}
    private operator fun Long.div(value:Double)=toDouble()/value
    private data class Position(val asOfDate:java.time.LocalDate,val warehouseId:String,val warehouseName:String,val lat:Double?,val lon:Double?,val skuId:String,val skuName:String,val safetyStock:Long,val usable:Long,val dailyDemand:BigDecimal,val unitCost:BigDecimal)
    private data class Need(val position:Position,val quantity:Long,val target:Long)
    private data class Source(val position:Position,var surplus:Long)
    companion object { private val SQL="""
        WITH latest AS (SELECT MAX(snapshot_date) as as_of_date FROM batch_inventory WHERE tenant_id=?),
        inventory AS (SELECT l.as_of_date,bi.warehouse_id,w.warehouse_name,w.latitude,w.longitude,bi.sku_id,s.sku_name,s.minimum_safety_stock,s.unit_cost,
          SUM(bi.available_quantity-bi.reserved_quantity-bi.blocked_quantity)::bigint usable_quantity
          FROM latest l JOIN batch_inventory bi ON bi.snapshot_date=l.as_of_date JOIN warehouse w ON w.tenant_id=bi.tenant_id AND w.warehouse_id=bi.warehouse_id
          JOIN sku s ON s.tenant_id=bi.tenant_id AND s.sku_id=bi.sku_id WHERE bi.tenant_id=? AND w.active=TRUE
          GROUP BY l.as_of_date,bi.warehouse_id,w.warehouse_name,w.latitude,w.longitude,bi.sku_id,s.sku_name,s.minimum_safety_stock,s.unit_cost),
        demand AS (SELECT warehouse_id,sku_id,SUM(sales_quantity)::numeric/30 daily_demand FROM sales_history,latest
          WHERE tenant_id=? AND sales_date BETWEEN latest.as_of_date-29 AND latest.as_of_date GROUP BY warehouse_id,sku_id)
        SELECT i.*,COALESCE(d.daily_demand,0) daily_demand FROM inventory i LEFT JOIN demand d USING(warehouse_id,sku_id)
    """.trimIndent() }
}
