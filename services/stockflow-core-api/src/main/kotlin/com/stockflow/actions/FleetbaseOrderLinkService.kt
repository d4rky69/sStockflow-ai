package com.stockflow.actions

import com.stockflow.fleetbase.FleetbaseTenantBinding
import com.stockflow.fleetbase.FleetbaseClient
import com.stockflow.fleetbase.FleetbaseIntegrationException
import com.stockflow.fleetbase.FleetbaseOrderCreateCommand
import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID

@Service
class FleetbaseOrderLinkService(
    private val jdbc: JdbcTemplate,
    private val tenantBinding: FleetbaseTenantBinding,
    private val fleetbaseClient: FleetbaseClient
) {
    private val mapper = RowMapper { rs: ResultSet, _: Int -> rs.toLink() }

    @Transactional
    fun prepare(actor: TenantAccessContext, executionId: UUID, idempotencyKey: String): FleetbaseOrderLinkView {
        validateKey(idempotencyKey)
        tenantBinding.requireMapped(actor.tenantId)
        val organizationId = tenantBinding.requireOrganizationId()
        val execution = lockExecution(actor, executionId)
        val fingerprint = fingerprint(actor.tenantId, organizationId, execution)

        byExecution(actor.tenantId, executionId)?.let { existing ->
            if (existing.requestFingerprint != fingerprint) throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The transfer execution no longer matches its prepared Fleetbase order snapshot"
            )
            return existing.view
        }
        byIdempotencyKey(actor.tenantId, idempotencyKey)?.let { existing ->
            if (existing.view.transferExecutionId != executionId) throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The Idempotency-Key is already assigned to a different transfer execution"
            )
            return existing.view
        }

        val linkId = UUID.randomUUID()
        val internalId = "SF-TRF-${executionId.toString().replace("-", "").take(12).uppercase()}"
        jdbc.update(
            """INSERT INTO fleetbase_order_link(
                link_id,tenant_id,transfer_execution_id,proposal_id,fleetbase_organization_id,
                fleetbase_internal_id,vehicle_id,link_status,idempotency_key,request_fingerprint,created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PREPARED', ?, ?, ?)""",
            linkId, actor.tenantId, executionId, execution.proposalId, organizationId,
            internalId, execution.vehicleReference, idempotencyKey, fingerprint, actor.userId
        )
        return require(actor, linkId).view
    }

    fun findForExecution(actor: TenantAccessContext, executionId: UUID): FleetbaseOrderLinkView? {
        val execution = execution(actor, executionId)
        requireWarehouse(actor, execution.sourceWarehouseId)
        requireWarehouse(actor, execution.destinationWarehouseId)
        return byExecution(actor.tenantId, executionId)?.view
    }

    fun get(actor: TenantAccessContext, linkId: UUID): FleetbaseOrderLinkView = require(actor, linkId).view

    @Transactional(noRollbackFor = [FleetbaseIntegrationException::class])
    fun createRemoteOrder(actor: TenantAccessContext, executionId: UUID, idempotencyKey: String): FleetbaseOrderLinkView {
        validateKey(idempotencyKey)
        tenantBinding.requireMapped(actor.tenantId)
        val expectedOrganization = tenantBinding.requireOrganizationId()
        val execution = lockExecution(actor, executionId)
        val link = lockLinkForExecution(actor.tenantId, executionId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Prepare the Fleetbase order link before creating the remote order")
        if (link.view.fleetbaseOrganizationId != expectedOrganization) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "The prepared Fleetbase link belongs to a different organization mapping"
        )
        if (link.view.status == "CREATED" || link.view.status == "DISPATCHED") return link.view
        if (link.view.status == "CANCELLED") throw ResponseStatusException(HttpStatus.CONFLICT, "A cancelled Fleetbase link cannot create an order")

        val source = warehouse(actor.tenantId, execution.sourceWarehouseId)
        val destination = warehouse(actor.tenantId, execution.destinationWarehouseId)
        jdbc.update(
            "UPDATE fleetbase_order_link SET attempt_count=attempt_count+1,last_error_code=NULL,last_error_message=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE link_id=?",
            link.view.linkId
        )
        val command = FleetbaseOrderCreateCommand(
            internalId = link.view.fleetbaseInternalId,
            pickup = source.place(),
            dropoff = destination.place(),
            vehicleId = execution.vehicleReference,
            notes = "StockFlow transfer ${execution.executionId}: ${execution.quantity} units of ${execution.skuId} from ${source.name} to ${destination.name}",
            meta = linkedMapOf(
                "stockflow_tenant_id" to actor.tenantId,
                "stockflow_execution_id" to execution.executionId.toString(),
                "stockflow_proposal_id" to execution.proposalId.toString(),
                "stockflow_sku_id" to execution.skuId,
                "stockflow_quantity" to execution.quantity,
                "stockflow_source_warehouse_id" to execution.sourceWarehouseId,
                "stockflow_destination_warehouse_id" to execution.destinationWarehouseId,
                "stockflow_dispatch_gate" to "FEFO_RESERVATION_REQUIRED"
            )
        )
        try {
            val created = fleetbaseClient.createOrder(command)
            if (created.internalId != null && created.internalId != link.view.fleetbaseInternalId) throw FleetbaseIntegrationException(
                HttpStatus.BAD_GATEWAY,
                "FLEETBASE_ORDER_IDENTITY_MISMATCH",
                "Fleetbase returned an order with a different internal identity"
            )
            jdbc.update(
                """UPDATE fleetbase_order_link SET fleetbase_order_id=?,link_status='CREATED',last_error_code=NULL,
                    last_error_message=NULL,remote_created_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1
                   WHERE link_id=?""",
                created.id, link.view.linkId
            )
        } catch (error: FleetbaseIntegrationException) {
            jdbc.update(
                """UPDATE fleetbase_order_link SET link_status='FAILED',last_error_code=?,last_error_message=?,
                    updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE link_id=?""",
                error.code, error.message.take(1000), link.view.linkId
            )
            throw error
        }
        return require(actor, link.view.linkId).view
    }

    /**
     * Dispatches an existing Fleetbase order only after StockFlow has locked and
     * verified the complete FEFO reservation. Executions without a Fleetbase link
     * retain the original StockFlow-only dispatch behaviour.
     */
    fun dispatchRemoteOrderIfLinked(actor: TenantAccessContext, executionId: UUID): FleetbaseOrderLinkView? {
        val execution = lockExecution(actor, executionId)
        if (execution.status != "RESERVED") throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "FEFO stock must be reserved before Fleetbase dispatch"
        )
        val link = lockLinkForExecution(actor.tenantId, executionId) ?: return null
        tenantBinding.requireMapped(actor.tenantId)
        val expectedOrganization = tenantBinding.requireOrganizationId()
        if (link.view.fleetbaseOrganizationId != expectedOrganization) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "The Fleetbase order belongs to a different organization mapping"
        )
        if (link.view.status == "DISPATCHED") return link.view
        if (link.view.status != "CREATED" || link.view.fleetbaseOrderId.isNullOrBlank()) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "Create the Fleetbase order before dispatching this linked transfer"
        )

        val reservation = jdbc.queryForMap(
            """SELECT COUNT(*) AS allocation_count,COALESCE(SUM(a.quantity),0) AS allocated_quantity,
                      COALESCE(SUM(CASE WHEN b.reserved_quantity>=a.quantity THEN a.quantity ELSE 0 END),0) AS secured_quantity
                 FROM transfer_execution_allocation a
                 JOIN batch_inventory b ON b.batch_inventory_id=a.source_batch_inventory_id
                WHERE a.tenant_id=? AND a.execution_id=?""",
            actor.tenantId, executionId
        )
        val allocationCount = (reservation["allocation_count"] as Number).toLong()
        val allocatedQuantity = (reservation["allocated_quantity"] as Number).toLong()
        val securedQuantity = (reservation["secured_quantity"] as Number).toLong()
        if (allocationCount == 0L || allocatedQuantity != execution.quantity || securedQuantity != execution.quantity) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "The complete FEFO reservation could not be verified; reserve or repair stock before dispatch"
        )

        jdbc.update(
            "UPDATE fleetbase_order_link SET attempt_count=attempt_count+1,last_error_code=NULL,last_error_message=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE link_id=?",
            link.view.linkId
        )
        val dispatched = fleetbaseClient.dispatchOrder(link.view.fleetbaseOrderId)
        if (!dispatched.dispatched) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_DISPATCH_NOT_CONFIRMED",
            "Fleetbase did not confirm dispatch"
        )
        jdbc.update(
            """UPDATE fleetbase_order_link SET link_status='DISPATCHED',last_error_code=NULL,last_error_message=NULL,
                dispatched_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE link_id=?""",
            link.view.linkId
        )
        return require(actor, link.view.linkId).view
    }

    fun tracking(actor: TenantAccessContext, executionId: UUID): FleetbaseTrackingView = synchronize(actor, executionId)

    fun reconcile(actor: TenantAccessContext, executionId: UUID): FleetbaseTrackingView = synchronize(actor, executionId)

    private fun synchronize(actor: TenantAccessContext, executionId: UUID): FleetbaseTrackingView {
        tenantBinding.requireMapped(actor.tenantId)
        val execution = execution(actor, executionId)
        requireWarehouse(actor, execution.sourceWarehouseId)
        requireWarehouse(actor, execution.destinationWarehouseId)
        val link = byExecution(actor.tenantId, executionId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "This transfer does not have a Fleetbase order link")
        if (link.view.fleetbaseOrganizationId != tenantBinding.requireOrganizationId()) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "The Fleetbase order belongs to a different organization mapping"
        )
        val orderId = link.view.fleetbaseOrderId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Create the Fleetbase order before requesting tracking")
        val remote = fleetbaseClient.getOrder(orderId)
        val tracker = fleetbaseClient.tracking(orderId)
        val reconciliation = reconciliation(execution.status, link.view.status, remote.status, remote.dispatched)
        jdbc.update(
            """UPDATE fleetbase_order_link SET remote_status=?,tracking_number=?,progress_percentage=?,eta_seconds=?,
                latitude=?,longitude=?,last_tracker_at=CURRENT_TIMESTAMP,last_reconciled_at=CURRENT_TIMESTAMP,
                reconciliation_status=?,last_error_code=NULL,last_error_message=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1
               WHERE tenant_id=? AND link_id=?""",
            remote.status, remote.trackingNumber, tracker.progressPercentage, tracker.completionEtaSeconds,
            tracker.latitude, tracker.longitude, reconciliation, actor.tenantId, link.view.linkId
        )
        return FleetbaseTrackingView(
            transferExecutionId = executionId,
            fleetbaseOrderId = orderId,
            remoteStatus = remote.status,
            trackingNumber = remote.trackingNumber,
            latitude = tracker.latitude,
            longitude = tracker.longitude,
            progressPercentage = tracker.progressPercentage,
            totalDistanceMeters = tracker.totalDistanceMeters,
            completedDistanceMeters = tracker.completedDistanceMeters,
            currentDestinationEtaSeconds = tracker.currentDestinationEtaSeconds,
            completionEtaSeconds = tracker.completionEtaSeconds,
            estimatedCompletionTime = tracker.estimatedCompletionTime,
            currentDestination = tracker.currentDestination,
            etaByDestination = tracker.etaByDestination,
            reconciliationStatus = reconciliation,
            synchronizedAt = LocalDateTime.now()
        )
    }

    private fun reconciliation(localExecution: String, linkStatus: String, remoteStatus: String?, remoteDispatched: Boolean): String {
        val normalizedRemote = remoteStatus?.uppercase()
        return when {
            localExecution == "RECEIVED" && normalizedRemote == "COMPLETED" -> "MATCHED"
            localExecution == "IN_TRANSIT" && remoteDispatched -> "MATCHED"
            localExecution == "RESERVED" && remoteDispatched -> "REMOTE_AHEAD"
            localExecution in setOf("IN_TRANSIT", "RECEIVED") && !remoteDispatched -> "LOCAL_AHEAD"
            linkStatus == "CREATED" && !remoteDispatched -> "MATCHED"
            else -> "REVIEW_REQUIRED"
        }
    }

    private fun lockExecution(actor: TenantAccessContext, executionId: UUID): ExecutionSnapshot {
        val result = jdbc.query(
            """SELECT execution_id,proposal_id,tenant_id,status,sku_id,source_warehouse_id,
                destination_warehouse_id,quantity,route_reference,vehicle_reference,version
               FROM transfer_execution WHERE tenant_id=? AND execution_id=? FOR UPDATE""",
            { rs, _ -> rs.toExecutionSnapshot() }, actor.tenantId, executionId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer execution was not found")
        requireWarehouse(actor, result.sourceWarehouseId)
        requireWarehouse(actor, result.destinationWarehouseId)
        if (result.status !in setOf("PLANNED", "RESERVED")) throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "A Fleetbase order link can only be prepared for a planned or reserved transfer execution"
        )
        return result
    }

    private fun execution(actor: TenantAccessContext, executionId: UUID): ExecutionSnapshot = jdbc.query(
        """SELECT execution_id,proposal_id,tenant_id,status,sku_id,source_warehouse_id,
            destination_warehouse_id,quantity,route_reference,vehicle_reference,version
           FROM transfer_execution WHERE tenant_id=? AND execution_id=?""",
        { rs, _ -> rs.toExecutionSnapshot() }, actor.tenantId, executionId
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer execution was not found")

    private fun require(actor: TenantAccessContext, linkId: UUID): StoredLink = jdbc.query(
        "SELECT * FROM fleetbase_order_link WHERE tenant_id=? AND link_id=?",
        mapper, actor.tenantId, linkId
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fleetbase order link was not found")

    private fun lockLinkForExecution(tenantId: String, executionId: UUID): StoredLink? = jdbc.query(
        "SELECT * FROM fleetbase_order_link WHERE tenant_id=? AND transfer_execution_id=? FOR UPDATE",
        mapper, tenantId, executionId
    ).firstOrNull()

    private fun byExecution(tenantId: String, executionId: UUID): StoredLink? = jdbc.query(
        "SELECT * FROM fleetbase_order_link WHERE tenant_id=? AND transfer_execution_id=?",
        mapper, tenantId, executionId
    ).firstOrNull()

    private fun byIdempotencyKey(tenantId: String, key: String): StoredLink? = jdbc.query(
        "SELECT * FROM fleetbase_order_link WHERE tenant_id=? AND idempotency_key=?",
        mapper, tenantId, key
    ).firstOrNull()

    private fun validateKey(key: String) {
        if (key.isBlank() || key.length > 160) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A valid Idempotency-Key header is required"
        )
    }

    private fun requireWarehouse(actor: TenantAccessContext, warehouseId: String) {
        if (actor.warehouseIds.isNotEmpty() && warehouseId !in actor.warehouseIds) throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Caller is not authorised for warehouse '$warehouseId'"
        )
    }

    private fun warehouse(tenantId: String, warehouseId: String): WarehouseSnapshot = jdbc.query(
        """SELECT warehouse_id,warehouse_name,city,state,country,latitude,longitude
           FROM warehouse WHERE tenant_id=? AND warehouse_id=? AND active=TRUE""",
        { rs, _ -> WarehouseSnapshot(
            id = rs.getString("warehouse_id"),
            name = rs.getString("warehouse_name"),
            city = rs.getString("city"),
            state = rs.getString("state"),
            country = rs.getString("country"),
            latitude = rs.getBigDecimal("latitude")?.toDouble(),
            longitude = rs.getBigDecimal("longitude")?.toDouble()
        ) }, tenantId, warehouseId
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Warehouse '$warehouseId' is not available for Fleetbase routing")

    private fun fingerprint(tenantId: String, organizationId: String, execution: ExecutionSnapshot): String {
        val canonical = listOf(
            tenantId, organizationId, execution.executionId, execution.proposalId, execution.skuId,
            execution.sourceWarehouseId, execution.destinationWarehouseId, execution.quantity,
            execution.routeReference.orEmpty(), execution.vehicleReference.orEmpty()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ResultSet.toExecutionSnapshot() = ExecutionSnapshot(
        executionId = UUID.fromString(getString("execution_id")),
        proposalId = UUID.fromString(getString("proposal_id")),
        status = getString("status"),
        skuId = getString("sku_id"),
        sourceWarehouseId = getString("source_warehouse_id"),
        destinationWarehouseId = getString("destination_warehouse_id"),
        quantity = getLong("quantity"),
        routeReference = getString("route_reference"),
        vehicleReference = getString("vehicle_reference")
    )

    private fun ResultSet.toLink(): StoredLink {
        val remoteOrderId = getString("fleetbase_order_id")
        return StoredLink(
            view = FleetbaseOrderLinkView(
                linkId = UUID.fromString(getString("link_id")),
                tenantId = getString("tenant_id"),
                transferExecutionId = UUID.fromString(getString("transfer_execution_id")),
                proposalId = UUID.fromString(getString("proposal_id")),
                fleetbaseOrganizationId = getString("fleetbase_organization_id"),
                fleetbaseOrderId = remoteOrderId,
                fleetbaseInternalId = getString("fleetbase_internal_id"),
                vehicleId = getString("vehicle_id"),
                status = getString("link_status"),
                attemptCount = getInt("attempt_count"),
                lastErrorCode = getString("last_error_code"),
                lastErrorMessage = getString("last_error_message"),
                createdBy = getString("created_by"),
                createdAt = getTimestamp("created_at").toLocalDateTime(),
                updatedAt = getTimestamp("updated_at").toLocalDateTime(),
                remoteCreatedAt = getTimestamp("remote_created_at")?.toLocalDateTime(),
                dispatchedAt = getTimestamp("dispatched_at")?.toLocalDateTime(),
                remoteWritePerformed = remoteOrderId != null,
                remoteStatus = getString("remote_status"),
                trackingNumber = getString("tracking_number"),
                progressPercentage = getBigDecimal("progress_percentage")?.toDouble(),
                etaSeconds = getObject("eta_seconds")?.let { (it as Number).toLong() },
                latitude = getBigDecimal("latitude")?.toDouble(),
                longitude = getBigDecimal("longitude")?.toDouble(),
                lastTrackerAt = getTimestamp("last_tracker_at")?.toLocalDateTime(),
                lastReconciledAt = getTimestamp("last_reconciled_at")?.toLocalDateTime(),
                lastWebhookAt = getTimestamp("last_webhook_at")?.toLocalDateTime(),
                reconciliationStatus = getString("reconciliation_status")
            ),
            requestFingerprint = getString("request_fingerprint")
        )
    }

    private data class ExecutionSnapshot(
        val executionId: UUID,
        val proposalId: UUID,
        val status: String,
        val skuId: String,
        val sourceWarehouseId: String,
        val destinationWarehouseId: String,
        val quantity: Long,
        val routeReference: String?,
        val vehicleReference: String?
    )

    private data class WarehouseSnapshot(
        val id: String,
        val name: String,
        val city: String,
        val state: String,
        val country: String,
        val latitude: Double?,
        val longitude: Double?
    ) {
        fun place(): Map<String, Any> {
            val place = linkedMapOf<String, Any>(
                "name" to name,
                "address" to listOf(name, city, state, country).filter { it.isNotBlank() }.joinToString(", "),
                "meta" to mapOf("stockflow_warehouse_id" to id)
            )
            if (latitude != null && longitude != null) place["location"] = mapOf("latitude" to latitude, "longitude" to longitude)
            return place
        }
    }

    private data class StoredLink(val view: FleetbaseOrderLinkView, val requestFingerprint: String)
}
