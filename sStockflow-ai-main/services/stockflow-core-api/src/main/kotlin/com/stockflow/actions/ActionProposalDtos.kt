package com.stockflow.actions

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CreateTransferProposalRequest(
    @field:NotBlank @field:Size(max = 80) val skuId: String,
    @field:DecimalMin("0.0001") val quantity: BigDecimal,
    @field:NotBlank @field:Size(max = 64) val sourceWarehouseId: String,
    @field:NotBlank @field:Size(max = 64) val destinationWarehouseId: String,
    @field:DecimalMin("0.00") val unitCost: BigDecimal? = null,
    @field:DecimalMin("0.00") val transportCost: BigDecimal? = null,
    @field:Size(min = 3, max = 3) val currency: String = "INR",
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    @field:Size(max = 10000) val recommendationEvidence: String? = null
)

data class CreatePurchaseProposalRequest(
    @field:NotBlank @field:Size(max = 80) val skuId: String,
    @field:DecimalMin("0.0001") val quantity: BigDecimal,
    @field:NotBlank @field:Size(max = 64) val destinationWarehouseId: String,
    @field:Size(max = 200) val supplierReference: String? = null,
    @field:DecimalMin("0.00") val unitCost: BigDecimal? = null,
    @field:Size(min = 3, max = 3) val currency: String = "INR",
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    @field:Size(max = 10000) val recommendationEvidence: String? = null
)

data class ProposalDecisionRequest(
    @field:Size(max = 1000) val comment: String? = null
)

data class ActionProposalView(
    val proposalId: UUID,
    val tenantId: String,
    val proposalType: String,
    val status: String,
    val skuId: String,
    val quantity: BigDecimal,
    val sourceWarehouseId: String?,
    val destinationWarehouseId: String?,
    val supplierReference: String?,
    val unitCost: BigDecimal?,
    val transportCost: BigDecimal?,
    val currency: String,
    val reason: String,
    val recommendationEvidence: String?,
    val idempotencyKey: String,
    val createdBy: String,
    val submittedBy: String?,
    val reviewedBy: String?,
    val reviewComment: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val submittedAt: LocalDateTime?,
    val reviewedAt: LocalDateTime?,
    val version: Long
)

data class ProposalHistoryView(
    val historyId: UUID,
    val proposalId: UUID,
    val fromStatus: String?,
    val toStatus: String,
    val changedBy: String,
    val comment: String?,
    val changedAt: LocalDateTime
)
