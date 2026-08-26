package com.stockflow.actions

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreatePurchaseOrderRequest(
    val expectedDeliveryDate: LocalDate? = null,
    @field:Size(max = 1000) val comment: String? = null
)

data class AcknowledgePurchaseOrderRequest(
    @field:Size(max = 200) val acknowledgementReference: String? = null,
    val expectedDeliveryDate: LocalDate? = null,
    @field:Size(max = 1000) val comment: String? = null
)

data class ReceivePurchaseOrderRequest(
    @field:Positive val quantity: Long,
    @field:NotBlank @field:Size(max = 100) val batchNumber: String,
    val manufactureDate: LocalDate? = null,
    val expiryDate: LocalDate,
    @field:DecimalMin("0.00") val unitCost: BigDecimal? = null,
    @field:NotBlank @field:Size(max = 40) val storageConditionCode: String = "AMBIENT",
    @field:Size(max = 1000) val comment: String? = null
)

data class PurchaseOrderView(
    val purchaseOrderId: UUID, val proposalId: UUID, val tenantId: String, val status: String,
    val skuId: String, val destinationWarehouseId: String, val supplierReference: String,
    val orderedQuantity: Long, val receivedQuantity: Long, val remainingQuantity: Long,
    val unitCost: BigDecimal, val currency: String, val expectedDeliveryDate: LocalDate?,
    val supplierAcknowledgementReference: String?, val createdBy: String, val sentBy: String?,
    val acknowledgedBy: String?, val lastReceivedBy: String?, val createdAt: LocalDateTime,
    val sentAt: LocalDateTime?, val acknowledgedAt: LocalDateTime?, val lastReceivedAt: LocalDateTime?,
    val updatedAt: LocalDateTime, val version: Long
)

data class PurchaseReceiptView(
    val receiptId: UUID, val quantity: Long, val batchNumber: String, val manufactureDate: LocalDate?,
    val expiryDate: LocalDate, val unitCost: BigDecimal, val storageConditionCode: String,
    val receivedBy: String, val receivedAt: LocalDateTime
)

data class PurchaseOrderEventView(
    val eventId: UUID, val fromStatus: String?, val toStatus: String, val changedBy: String,
    val comment: String?, val occurredAt: LocalDateTime
)

data class PurchaseOrderDetail(
    val purchaseOrder: PurchaseOrderView,
    val receipts: List<PurchaseReceiptView>,
    val events: List<PurchaseOrderEventView>
)
