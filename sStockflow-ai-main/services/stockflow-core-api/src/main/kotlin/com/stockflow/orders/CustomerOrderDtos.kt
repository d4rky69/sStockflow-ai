package com.stockflow.orders

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CreateCustomerOrderRequest(
    @field:NotBlank @field:Size(max = 200) val customerName: String,
    @field:NotBlank @field:Size(max = 120) val customerCity: String,
    @field:NotBlank @field:Size(max = 80) val channel: String,
    @field:NotBlank @field:Size(max = 64) val warehouseId: String,
    @field:NotBlank @field:Size(max = 80) val skuId: String,
    @field:Positive val quantity: Long,
    @field:Future val promisedAt: LocalDateTime,
    @field:DecimalMin("0.00") val unitPrice: BigDecimal? = null
)

data class CustomerOrderView(
    val orderId: UUID,
    val orderNumber: String,
    val tenantId: String,
    val customerName: String,
    val customerCity: String,
    val channel: String,
    val warehouseId: String,
    val warehouseName: String,
    val status: String,
    val promisedAt: LocalDateTime,
    val fulfilmentPercent: Int,
    val totalValue: BigDecimal,
    val currency: String,
    val itemCount: Int,
    val skuId: String,
    val skuName: String,
    val quantity: Long,
    val unitPrice: BigDecimal,
    val createdBy: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val version: Long
)

data class CustomerOrderEventView(
    val eventId: UUID,
    val fromStatus: String?,
    val toStatus: String,
    val changedBy: String,
    val comment: String?,
    val occurredAt: LocalDateTime
)

data class CustomerOrderDetail(val order: CustomerOrderView, val events: List<CustomerOrderEventView>)

data class AdvanceCustomerOrderRequest(@field:Size(max = 1000) val comment: String? = null)
