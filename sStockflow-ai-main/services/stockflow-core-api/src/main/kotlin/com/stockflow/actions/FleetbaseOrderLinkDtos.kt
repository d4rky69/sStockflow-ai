package com.stockflow.actions

import java.time.LocalDateTime
import java.util.UUID

data class FleetbaseOrderLinkView(
    val linkId: UUID,
    val tenantId: String,
    val transferExecutionId: UUID,
    val proposalId: UUID,
    val fleetbaseOrganizationId: String,
    val fleetbaseOrderId: String?,
    val fleetbaseInternalId: String,
    val vehicleId: String?,
    val status: String,
    val attemptCount: Int,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdBy: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val remoteCreatedAt: LocalDateTime?,
    val dispatchedAt: LocalDateTime?,
    val remoteWritePerformed: Boolean,
    val remoteStatus: String? = null,
    val trackingNumber: String? = null,
    val progressPercentage: Double? = null,
    val etaSeconds: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastTrackerAt: LocalDateTime? = null,
    val lastReconciledAt: LocalDateTime? = null,
    val lastWebhookAt: LocalDateTime? = null,
    val reconciliationStatus: String = "NOT_CHECKED"
)

data class FleetbaseTrackingView(
    val transferExecutionId: UUID,
    val fleetbaseOrderId: String,
    val remoteStatus: String?,
    val trackingNumber: String?,
    val latitude: Double?,
    val longitude: Double?,
    val progressPercentage: Double?,
    val totalDistanceMeters: Long?,
    val completedDistanceMeters: Long?,
    val currentDestinationEtaSeconds: Long?,
    val completionEtaSeconds: Long?,
    val estimatedCompletionTime: String?,
    val currentDestination: String?,
    val etaByDestination: Map<String, Long>,
    val reconciliationStatus: String,
    val synchronizedAt: LocalDateTime
)

data class FleetbaseAuditSummaryView(
    val tenantId: String,
    val totalLinks: Int,
    val prepared: Int,
    val created: Int,
    val dispatched: Int,
    val failed: Int,
    val reconciliationIssues: Int,
    val webhookEvents: Int,
    val lastWebhookAt: LocalDateTime?,
    val writesEnabled: Boolean,
    val webhookConfigured: Boolean,
    val rolloutStatus: String
)
