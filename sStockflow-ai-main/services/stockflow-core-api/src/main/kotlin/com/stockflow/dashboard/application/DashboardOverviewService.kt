package com.stockflow.dashboard.application

import com.stockflow.analytics.application.DemandAnalyticsService
import com.stockflow.intelligence.application.InventoryIntelligenceQueryService
import com.stockflow.risk.application.InventoryRiskService
import com.stockflow.risk.application.InventoryRiskView
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class DashboardOverviewService(
    private val inventoryRiskService: InventoryRiskService,
    private val demandAnalyticsService: DemandAnalyticsService,
    private val intelligenceQueryService: InventoryIntelligenceQueryService,
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun getOverview(tenantId: String, warehouseId: String? = null): DashboardOverviewView {
        val riskSummary = inventoryRiskService.summary(tenantId)
        val selectedWarehouseId = warehouseId?.trim()?.takeIf { it.isNotEmpty() }
        val risks = inventoryRiskService.risks(tenantId, null, null, 500)
            .filter { selectedWarehouseId == null || it.warehouseId == selectedWarehouseId }
        val positions = intelligenceQueryService.inventoryPositions(tenantId, riskSummary.asOfDate)
            .filter { selectedWarehouseId == null || it.warehouseId == selectedWarehouseId }
        val demandTrend = demandAnalyticsService.trend(tenantId, 16)

        val inventoryValue = positions.fold(BigDecimal.ZERO) { total, item -> total + item.inventoryValue }
        val blockedValue = positions.fold(BigDecimal.ZERO) { total, item -> total + item.blockedValue }
        val nearExpiryValue = risks
            .filter { it.riskType == "NEAR_EXPIRY" || it.riskType == "EXPIRED_INVENTORY" }
            .fold(BigDecimal.ZERO) { total, item -> total + item.inventoryValue }
        val excessValue = risks
            .filter { it.riskType == "EXCESS_INVENTORY" || it.riskType == "SLOW_MOVING" }
            .fold(BigDecimal.ZERO) { total, item -> total + item.inventoryValue }

        val totalRiskCount = risks.size
        val expiryCount = risks.count { it.riskType == "NEAR_EXPIRY" || it.riskType == "EXPIRED_INVENTORY" }
        val stockoutCount = risks.count { it.riskType == "STOCKOUT_RISK" || it.riskType == "SAFETY_STOCK_BREACH" }
        val excessCount = risks.count { it.riskType == "EXCESS_INVENTORY" || it.riskType == "SLOW_MOVING" }
        val demandSurgeCount = risks.count { it.riskType == "DEMAND_SURGE" }
        val dataGapCount = risks.count { it.riskType == "INVENTORY_DATA_GAP" }
        val operationalRiskCount = (totalRiskCount - dataGapCount).coerceAtLeast(0)
        val operationalRisks = risks.filter { it.riskType != "INVENTORY_DATA_GAP" }

        return DashboardOverviewView(
            userName = "StockFlow User",
            userRole = "Inventory Manager",
            asOf = riskSummary.asOfDate.toString(),
            kpis = listOf(
                kpi("inventoryValue", "Total Inventory Value", formatInr(inventoryValue), "Live", "PostgreSQL", "up", "neutral", "◇", "linear-gradient(145deg,#4f2ee9,#6b4cff)"),
                kpi("stockoutRisk", "Operational Stock Risk", stockoutCount.toString(), "$dataGapCount data gaps", "excluded from stock risk", "down", if (stockoutCount > 0) "negative" else "positive", "🛒", "linear-gradient(145deg,#1689ff,#006be6)"),
                kpi("nearExpiry", "Near Expiry Value", formatInr(nearExpiryValue), "${expiryCount} batches", "within 60 days", "up", if (expiryCount > 0) "negative" else "positive", "▣", "linear-gradient(145deg,#ff9d00,#ff7a00)"),
                kpi("excess", "Excess / Slow Inventory", formatInr(excessValue), "${excessCount} risks", "days-of-cover rules", "down", if (excessCount > 0) "negative" else "positive", "↗", "linear-gradient(145deg,#18b967,#0ba750)"),
                kpi("workingCapital", "Reserved / Blocked Value", formatInr(blockedValue), "Live", "current snapshot", "up", "neutral", "▤", "linear-gradient(145deg,#17b3ce,#0698b9)")
            ),
            riskTotal = totalRiskCount,
            riskBreakdown = listOf(
                breakdown("Near Expiry", expiryCount, totalRiskCount, "#f5534b"),
                breakdown("Stockout / Safety", stockoutCount, totalRiskCount, "#ff9a1f"),
                breakdown("Excess / Slow Moving", excessCount, totalRiskCount, "#2f9af5"),
                breakdown("Demand Surge", demandSurgeCount, totalRiskCount, "#1eb266"),
                breakdown("Inventory Data Gaps", dataGapCount, totalRiskCount, "#6849e8")
            ),
            topRisks = operationalRisks.take(6).map(::topRisk),
            demandForecast = DashboardChartSeriesView(
                labels = demandTrend.labels,
                actual = demandTrend.actual,
                forecast = demandTrend.forecast,
                values = emptyList()
            ),
            inventoryTrend = DashboardChartSeriesView(
                labels = listOf(riskSummary.asOfDate.toString()),
                actual = emptyList(),
                forecast = emptyList(),
                values = listOf(inventoryValue.divide(BigDecimal.valueOf(10_000_000), 4, RoundingMode.HALF_UP).toDouble())
            ),
            recommendations = operationalRisks.take(3).map(::recommendation),
            networkMetrics = listOf(
                DashboardNetworkMetricView("Warehouses", count("warehouse", tenantId).toString(), "⌂"),
                DashboardNetworkMetricView("Retailers", count("retailer", tenantId).toString(), "▥"),
                DashboardNetworkMetricView("SKUs", count("sku", tenantId).toString(), "◇"),
                DashboardNetworkMetricView("Inventory Batches", count("batch_inventory", tenantId).toString(), "▤")
            ),
            copilotMessages = listOf(
                DashboardCopilotMessageView(
                    role = "assistant",
                    text = "The dashboard is calculated from PostgreSQL. I found $operationalRiskCount operational risks and $dataGapCount inventory-data gaps as of ${riskSummary.asOfDate}.",
                    timestamp = "Live"
                )
            )
        )
    }

    private fun count(table: String, tenantId: String): Long {
        val permitted = setOf("warehouse", "retailer", "sku", "batch_inventory")
        require(table in permitted) { "Unsupported dashboard table" }
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $table WHERE tenant_id = :tenantId",
            mapOf("tenantId" to tenantId),
            Long::class.java
        ) ?: 0L
    }

    private fun kpi(
        key: String, label: String, value: String, change: String, comparison: String,
        direction: String, intent: String, icon: String, accent: String
    ) = DashboardKpiView(key, label, value, change, comparison, direction, intent, icon, accent)

    private fun breakdown(label: String, count: Int, total: Int, color: String): DashboardRiskBreakdownView =
        DashboardRiskBreakdownView(
            label = label,
            count = count,
            percentage = if (total == 0) 0 else ((count * 100.0) / total).toInt(),
            color = color
        )

    private fun topRisk(risk: InventoryRiskView): DashboardTopRiskView = DashboardTopRiskView(
        id = risk.riskId,
        product = risk.skuName,
        batch = risk.batchNumber,
        warehouse = risk.warehouseName,
        type = risk.riskType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
        badgeClass = when (risk.riskType) {
            "NEAR_EXPIRY", "EXPIRED_INVENTORY" -> "expiry"
            "STOCKOUT_RISK", "SAFETY_STOCK_BREACH" -> "stockout"
            "DEMAND_SURGE", "INVENTORY_DATA_GAP" -> "surge"
            else -> "excess"
        },
        quantity = "${risk.usableQuantity} Units",
        detail = risk.reason,
        metric = when {
            risk.daysToExpiry != null -> "${risk.daysToExpiry} days to expiry"
            risk.daysOfCover != null -> "${risk.daysOfCover} days cover"
            else -> risk.severity
        },
        metricIntent = if (risk.severity == "CRITICAL" || risk.severity == "HIGH") "high" else "neutral",
        icon = when (risk.riskType) {
            "NEAR_EXPIRY", "EXPIRED_INVENTORY" -> "♧"
            "STOCKOUT_RISK", "SAFETY_STOCK_BREACH" -> "▤"
            "DEMAND_SURGE" -> "🛒"
            "INVENTORY_DATA_GAP" -> "!"
            else -> "◇"
        }
    )

    private fun recommendation(risk: InventoryRiskView): DashboardRecommendationView = DashboardRecommendationView(
        title = risk.recommendedAction,
        subtitle = "${risk.skuName} · ${risk.warehouseName}",
        benefit = if (risk.inventoryValue > BigDecimal.ZERO) formatInr(risk.inventoryValue) else "Service level",
        icon = when (risk.riskType) {
            "STOCKOUT_RISK", "SAFETY_STOCK_BREACH" -> "⇄"
            "NEAR_EXPIRY", "EXPIRED_INVENTORY" -> "⚑"
            else -> "▣"
        }
    )

    private fun formatInr(value: BigDecimal): String {
        val absolute = value.abs()
        return when {
            absolute >= BigDecimal.valueOf(10_000_000) -> "₹${value.divide(BigDecimal.valueOf(10_000_000), 2, RoundingMode.HALF_UP)} Cr"
            absolute >= BigDecimal.valueOf(100_000) -> "₹${value.divide(BigDecimal.valueOf(100_000), 2, RoundingMode.HALF_UP)} L"
            else -> "₹${value.setScale(2, RoundingMode.HALF_UP)}"
        }
    }
}
