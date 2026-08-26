package com.stockflow.risk.application

import com.stockflow.intelligence.application.DemandPositionMetric
import com.stockflow.intelligence.application.InventoryIntelligenceQueryService
import com.stockflow.intelligence.application.InventoryPositionMetric
import com.stockflow.intelligence.application.PositionKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class InventoryRiskService(
    private val intelligenceQueryService: InventoryIntelligenceQueryService,
    @Value("\${stockflow.intelligence.stockout-cover-days:14}") private val stockoutCoverDays: Int,
    @Value("\${stockflow.intelligence.excess-cover-days:90}") private val excessCoverDays: Int,
    @Value("\${stockflow.intelligence.near-expiry-days:60}") private val defaultNearExpiryDays: Int,
    @Value("\${stockflow.intelligence.demand-surge-percent:50}") private val demandSurgePercent: Int
) {
    fun summary(tenantId: String): InventoryRiskSummaryView {
        val risks = calculate(tenantId, defaultNearExpiryDays)
        val asOfDate = risks.firstOrNull()?.asOfDate
            ?: intelligenceQueryService.inventoryAsOfDate(tenantId)
            ?: intelligenceQueryService.salesAsOfDate(tenantId)
            ?: LocalDate.now()
        return InventoryRiskSummaryView(
            tenantId = tenantId,
            asOfDate = asOfDate,
            totalRisks = risks.size,
            criticalCount = risks.count { it.severity == "CRITICAL" },
            highCount = risks.count { it.severity == "HIGH" },
            mediumCount = risks.count { it.severity == "MEDIUM" },
            stockoutRiskCount = risks.count { it.riskType == "STOCKOUT_RISK" },
            safetyStockBreachCount = risks.count { it.riskType == "SAFETY_STOCK_BREACH" },
            inventoryDataGapCount = risks.count { it.riskType == "INVENTORY_DATA_GAP" },
            nearExpiryCount = risks.count { it.riskType == "NEAR_EXPIRY" },
            expiredCount = risks.count { it.riskType == "EXPIRED_INVENTORY" },
            excessInventoryCount = risks.count { it.riskType == "EXCESS_INVENTORY" },
            slowMovingCount = risks.count { it.riskType == "SLOW_MOVING" },
            demandSurgeCount = risks.count { it.riskType == "DEMAND_SURGE" },
            riskExposureValue = risks.fold(BigDecimal.ZERO) { total, risk -> total + risk.inventoryValue }
                .setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun risks(
        tenantId: String,
        riskType: String?,
        severity: String?,
        limit: Int
    ): List<InventoryRiskView> {
        val normalizedType = riskType?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val normalizedSeverity = severity?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return calculate(tenantId, defaultNearExpiryDays)
            .asSequence()
            .filter { normalizedType == null || it.riskType == normalizedType }
            .filter { normalizedSeverity == null || it.severity == normalizedSeverity }
            .take(limit.coerceIn(1, 500))
            .toList()
    }

    fun stockoutRisks(tenantId: String, limit: Int): List<InventoryRiskView> =
        calculate(tenantId, defaultNearExpiryDays)
            .filter { it.riskType == "STOCKOUT_RISK" || it.riskType == "SAFETY_STOCK_BREACH" }
            .take(limit.coerceIn(1, 500))

    fun expiryRisks(tenantId: String, days: Int, limit: Int): List<InventoryRiskView> =
        calculate(tenantId, days.coerceIn(1, 365))
            .filter { it.riskType == "NEAR_EXPIRY" || it.riskType == "EXPIRED_INVENTORY" }
            .take(limit.coerceIn(1, 500))

    fun calculate(tenantId: String, nearExpiryDays: Int = defaultNearExpiryDays): List<InventoryRiskView> {
        val asOfDate = intelligenceQueryService.inventoryAsOfDate(tenantId)
            ?: intelligenceQueryService.salesAsOfDate(tenantId)
            ?: LocalDate.now()
        val inventoryByKey = intelligenceQueryService.inventoryPositions(tenantId, asOfDate).associateBy { it.key() }
        val demandByKey = intelligenceQueryService.demandPositions(tenantId, asOfDate).associateBy { it.key() }
        val keys = (inventoryByKey.keys + demandByKey.keys)
            .distinct()
            .sortedWith(compareBy<PositionKey> { it.warehouseId }.thenBy { it.skuId })
        val risks = mutableListOf<InventoryRiskView>()

        keys.forEach { key ->
            val inventory = inventoryByKey[key]
            val demand = demandByKey[key]
            val average30 = averageDaily(demand?.sales30 ?: 0L, 30)
            val daysOfCover = inventory?.let { daysOfCover(it.usableQuantity, average30) }
            val minimumSafetyStock = inventory?.minimumSafetyStock ?: demand?.minimumSafetyStock ?: 0L
            val usableQuantity = inventory?.usableQuantity ?: 0L
            val hasDemand = (demand?.sales30 ?: 0L) > 0

            when {
                inventory == null && hasDemand -> {
                    risks += positionRisk(
                        tenantId, asOfDate, "INVENTORY_DATA_GAP", "MEDIUM", inventory, demand,
                        average30, null,
                        "Demand exists, but no inventory snapshot is available for this warehouse and SKU",
                        "Load or reconcile the warehouse-SKU inventory snapshot before making a replenishment decision"
                    )
                }
                inventory != null && hasDemand &&
                    (usableQuantity == 0L || (daysOfCover != null && daysOfCover <= bd(stockoutCoverDays))) -> {
                    val severity = if (usableQuantity == 0L || (daysOfCover != null && daysOfCover <= bd(7))) "CRITICAL" else "HIGH"
                    risks += positionRisk(
                        tenantId, asOfDate, "STOCKOUT_RISK", severity, inventory, demand,
                        average30, daysOfCover,
                        if (usableQuantity == 0L) "Recent demand exists and the current inventory snapshot has no usable stock" else
                            "Days of cover is ${daysOfCover?.setScale(1, RoundingMode.HALF_UP)} against the $stockoutCoverDays-day threshold",
                        "Expedite replenishment or transfer stock from a warehouse with surplus inventory"
                    )
                }
                inventory != null && usableQuantity < minimumSafetyStock -> {
                    risks += positionRisk(
                        tenantId, asOfDate, "SAFETY_STOCK_BREACH", "HIGH", inventory, demand,
                        average30, daysOfCover,
                        "Usable quantity $usableQuantity is below minimum safety stock $minimumSafetyStock",
                        "Replenish at least ${minimumSafetyStock - usableQuantity} units, rounded to the SKU reorder multiple"
                    )
                }
                daysOfCover != null && daysOfCover > bd(excessCoverDays) -> {
                    risks += positionRisk(
                        tenantId, asOfDate, "EXCESS_INVENTORY", "MEDIUM", inventory, demand,
                        average30, daysOfCover,
                        "Days of cover is ${daysOfCover.setScale(1, RoundingMode.HALF_UP)}, above the $excessCoverDays-day threshold",
                        "Postpone procurement and evaluate transfer or promotion options"
                    )
                }
                inventory != null && !hasDemand && usableQuantity > 0 -> {
                    risks += positionRisk(
                        tenantId, asOfDate, "SLOW_MOVING", "MEDIUM", inventory, demand,
                        average30, daysOfCover,
                        "No sales were recorded in the last 30 days while usable inventory remains",
                        "Review demand, stop replenishment and consider redistribution or promotion"
                    )
                }
            }

            val surgePercent = surgePercent(demand)
            if (surgePercent != null && surgePercent >= BigDecimal.valueOf(demandSurgePercent.toLong())) {
                risks += positionRisk(
                    tenantId, asOfDate, "DEMAND_SURGE", if (surgePercent >= bd(100)) "HIGH" else "MEDIUM",
                    inventory, demand, average30, daysOfCover,
                    "Seven-day demand velocity is ${surgePercent.setScale(1, RoundingMode.HALF_UP)}% above the 30-day baseline",
                    "Increase the short-term replenishment target and verify the underlying demand signal"
                )
            }
        }

        intelligenceQueryService.expiryBatches(tenantId, asOfDate).forEach { batch ->
            val daysToExpiry = ChronoUnit.DAYS.between(asOfDate, batch.expiryDate)
            if (batch.usableQuantity > 0 && daysToExpiry <= nearExpiryDays) {
                val expired = daysToExpiry < 0
                risks += InventoryRiskView(
                    riskId = "RISK-${if (expired) "EXPIRED" else "EXPIRY"}-${batch.batchInventoryId}",
                    tenantId = tenantId,
                    riskType = if (expired) "EXPIRED_INVENTORY" else "NEAR_EXPIRY",
                    severity = if (expired || daysToExpiry <= 15) "CRITICAL" else if (daysToExpiry <= 30) "HIGH" else "MEDIUM",
                    asOfDate = asOfDate,
                    warehouseId = batch.warehouseId,
                    warehouseName = batch.warehouseName,
                    skuId = batch.skuId,
                    skuName = batch.skuName,
                    batchNumber = batch.batchNumber,
                    availableQuantity = batch.availableQuantity,
                    usableQuantity = batch.usableQuantity,
                    minimumSafetyStock = 0,
                    sales7 = 0,
                    sales30 = 0,
                    averageDailyDemand30 = BigDecimal.ZERO.setScale(2),
                    daysOfCover = null,
                    expiryDate = batch.expiryDate,
                    daysToExpiry = daysToExpiry,
                    inventoryValue = batch.inventoryValue.setScale(2, RoundingMode.HALF_UP),
                    lostSales30 = 0,
                    stockoutRows30 = 0,
                    reason = if (expired) "Batch expired ${-daysToExpiry} days ago" else "Batch expires in $daysToExpiry days",
                    recommendedAction = if (expired) "Quarantine the batch and initiate the approved disposal process" else
                        "Prioritise FEFO dispatch, transfer to a faster-moving warehouse or run a controlled promotion"
                )
            }
        }

        return risks.sortedWith(
            compareBy<InventoryRiskView> { severityRank(it.severity) }
                .thenBy { typeRank(it.riskType) }
                .thenByDescending { it.inventoryValue }
                .thenBy { it.warehouseId }
                .thenBy { it.skuId }
        )
    }

    private fun positionRisk(
        tenantId: String,
        asOfDate: LocalDate,
        riskType: String,
        severity: String,
        inventory: InventoryPositionMetric?,
        demand: DemandPositionMetric?,
        average30: BigDecimal,
        daysOfCover: BigDecimal?,
        reason: String,
        recommendedAction: String
    ): InventoryRiskView {
        val warehouseId = inventory?.warehouseId ?: demand!!.warehouseId
        val skuId = inventory?.skuId ?: demand!!.skuId
        return InventoryRiskView(
            riskId = "RISK-$riskType-$warehouseId-$skuId",
            tenantId = tenantId,
            riskType = riskType,
            severity = severity,
            asOfDate = asOfDate,
            warehouseId = warehouseId,
            warehouseName = inventory?.warehouseName ?: demand!!.warehouseName,
            skuId = skuId,
            skuName = inventory?.skuName ?: demand!!.skuName,
            batchNumber = null,
            availableQuantity = inventory?.availableQuantity ?: 0,
            usableQuantity = inventory?.usableQuantity ?: 0,
            minimumSafetyStock = inventory?.minimumSafetyStock ?: demand?.minimumSafetyStock ?: 0,
            sales7 = demand?.sales7 ?: 0,
            sales30 = demand?.sales30 ?: 0,
            averageDailyDemand30 = average30,
            daysOfCover = daysOfCover?.setScale(2, RoundingMode.HALF_UP),
            expiryDate = inventory?.nextExpiryDate,
            daysToExpiry = inventory?.nextExpiryDate?.let { ChronoUnit.DAYS.between(asOfDate, it) },
            inventoryValue = (inventory?.inventoryValue ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
            lostSales30 = demand?.lostSales30 ?: 0,
            stockoutRows30 = demand?.stockoutRows30 ?: 0,
            reason = reason,
            recommendedAction = recommendedAction
        )
    }

    private fun averageDaily(quantity: Long, days: Int): BigDecimal =
        BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(days.toLong()), 4, RoundingMode.HALF_UP)

    private fun daysOfCover(usableQuantity: Long, averageDailyDemand: BigDecimal): BigDecimal? =
        if (averageDailyDemand.compareTo(BigDecimal.ZERO) <= 0) null else
            BigDecimal.valueOf(usableQuantity).divide(averageDailyDemand, 4, RoundingMode.HALF_UP)

    private fun surgePercent(demand: DemandPositionMetric?): BigDecimal? {
        if (demand == null || demand.sales7 <= 0 || demand.sales30 <= 0) return null
        val average7 = averageDaily(demand.sales7, 7)
        val average30 = averageDaily(demand.sales30, 30)
        if (average30.compareTo(BigDecimal.ZERO) == 0) return null
        return average7.subtract(average30).multiply(BigDecimal.valueOf(100))
            .divide(average30, 2, RoundingMode.HALF_UP)
    }

    private fun bd(value: Int): BigDecimal = BigDecimal.valueOf(value.toLong())

    private fun severityRank(severity: String): Int = when (severity) {
        "CRITICAL" -> 0
        "HIGH" -> 1
        else -> 2
    }

    private fun typeRank(type: String): Int = when (type) {
        "EXPIRED_INVENTORY" -> 0
        "STOCKOUT_RISK" -> 1
        "SAFETY_STOCK_BREACH" -> 2
        "NEAR_EXPIRY" -> 3
        "DEMAND_SURGE" -> 4
        "EXCESS_INVENTORY" -> 5
        "INVENTORY_DATA_GAP" -> 6
        else -> 7
    }
}
