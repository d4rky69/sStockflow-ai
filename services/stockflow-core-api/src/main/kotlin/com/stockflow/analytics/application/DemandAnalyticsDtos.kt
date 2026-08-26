package com.stockflow.analytics.application

import java.math.BigDecimal
import java.time.LocalDate

data class DemandSummaryView(
    val tenantId: String,
    val asOfDate: LocalDate,
    val windowDays: Int,
    val transactionRows: Long,
    val skuCount: Long,
    val warehouseCount: Long,
    val salesQuantity: Long,
    val returnQuantity: Long,
    val lostSalesQuantity: Long,
    val stockoutRows: Long,
    val averageDailyDemand: BigDecimal,
    val grossSalesValue: BigDecimal,
    val fulfilmentRatePercent: BigDecimal
)

data class DemandSkuView(
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val salesQuantity: Long,
    val returnQuantity: Long,
    val lostSalesQuantity: Long,
    val stockoutRows: Long,
    val averageDailyDemand: BigDecimal,
    val grossSalesValue: BigDecimal
)

data class DemandTrendView(
    val tenantId: String,
    val asOfDate: LocalDate,
    val labels: List<String>,
    val actual: List<Long>,
    val forecast: List<Long>
)
