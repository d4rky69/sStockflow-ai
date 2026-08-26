package com.stockflow.actions

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CreateTransferExecutionRequest(
    @field:Size(max = 160) val routeReference: String? = null,
    @field:Size(max = 160) val vehicleReference: String? = null,
    @field:Size(max = 1000) val comment: String? = null
)

data class ReceiveTransferRequest(
    @field:DecimalMin("0.00") val actualTransportCost: BigDecimal? = null,
    @field:DecimalMin("0.00") val actualCarbonKg: BigDecimal? = null,
    @field:Size(max = 1000) val comment: String? = null
)

data class TransferExecutionView(
    val executionId: UUID, val proposalId: UUID, val tenantId: String, val status: String,
    val skuId: String, val sourceWarehouseId: String, val destinationWarehouseId: String,
    val quantity: Long, val routeReference: String?, val vehicleReference: String?,
    val actualTransportCost: BigDecimal?, val actualCarbonKg: BigDecimal?, val createdBy: String,
    val dispatchedBy: String?, val receivedBy: String?, val createdAt: LocalDateTime,
    val reservedAt: LocalDateTime?, val dispatchedAt: LocalDateTime?, val receivedAt: LocalDateTime?,
    val updatedAt: LocalDateTime, val version: Long
)

data class TransferAllocationView(val batchNumber: String, val quantity: Long, val expiryDate: String, val unitCost: BigDecimal, val currency: String)
data class TransferExecutionEventView(val eventId: UUID, val fromStatus: String?, val toStatus: String, val changedBy: String, val comment: String?, val occurredAt: LocalDateTime)
data class TransferExecutionDetail(
    val execution: TransferExecutionView,
    val allocations: List<TransferAllocationView>,
    val events: List<TransferExecutionEventView>,
    val fleetbaseOrderLink: FleetbaseOrderLinkView?
)
