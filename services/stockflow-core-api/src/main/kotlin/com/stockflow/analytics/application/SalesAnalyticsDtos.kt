package com.stockflow.analytics.application

import java.math.BigDecimal
import java.time.LocalDate

data class SalesSummaryView(
    val tenantId: String,
    val dateFrom: LocalDate?,
    val dateTo: LocalDate?,
    val transactionRows: Long,
    val orderedQuantity: Long,
    val fulfilledQuantity: Long,
    val salesQuantity: Long,
    val returnQuantity: Long,
    val lostSalesQuantity: Long,
    val grossSalesValue: BigDecimal,
    val stockoutRows: Long,
    val fulfilmentRatePercent: BigDecimal
)

data class TopSkuSalesView(
    val skuId: String,
    val skuName: String,
    val salesQuantity: Long,
    val grossSalesValue: BigDecimal,
    val lostSalesQuantity: Long,
    val stockoutRows: Long
)
