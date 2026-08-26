package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID

@Service
class ActionProposalService(private val jdbcTemplate: JdbcTemplate) {
    private val proposalMapper = RowMapper { rs: ResultSet, _: Int -> rs.toProposal() }

    @Transactional
    fun createTransfer(actor: TenantAccessContext, idempotencyKey: String, request: CreateTransferProposalRequest): ActionProposalView {
        requireIdempotency(idempotencyKey)
        validateSku(actor.tenantId, request.skuId)
        validateWarehouse(actor.tenantId, request.sourceWarehouseId)
        validateWarehouse(actor.tenantId, request.destinationWarehouseId)
        requireWarehouseAccess(actor, request.sourceWarehouseId)
        requireWarehouseAccess(actor, request.destinationWarehouseId)
        if (request.sourceWarehouseId == request.destinationWarehouseId) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination warehouses must differ")
        existingByKey(actor.tenantId, idempotencyKey)?.let { existing ->
            if (existing.proposalType == "TRANSFER" && existing.skuId == request.skuId && existing.quantity.compareTo(request.quantity) == 0 && existing.sourceWarehouseId == request.sourceWarehouseId && existing.destinationWarehouseId == request.destinationWarehouseId) return existing
            throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used for a different proposal")
        }
        rejectDuplicate(actor.tenantId, "TRANSFER", request.skuId, request.sourceWarehouseId, request.destinationWarehouseId, null)
        return insert(actor, idempotencyKey, "TRANSFER", request.skuId, request.quantity, request.sourceWarehouseId, request.destinationWarehouseId, null, request.unitCost, request.transportCost, request.currency, request.reason, request.recommendationEvidence)
    }

    @Transactional
    fun createPurchase(actor: TenantAccessContext, idempotencyKey: String, request: CreatePurchaseProposalRequest): ActionProposalView {
        requireIdempotency(idempotencyKey)
        validateSku(actor.tenantId, request.skuId)
        validateWarehouse(actor.tenantId, request.destinationWarehouseId)
        requireWarehouseAccess(actor, request.destinationWarehouseId)
        existingByKey(actor.tenantId, idempotencyKey)?.let { existing ->
            if (existing.proposalType == "PURCHASE" && existing.skuId == request.skuId && existing.quantity.compareTo(request.quantity) == 0 && existing.destinationWarehouseId == request.destinationWarehouseId && existing.supplierReference == request.supplierReference) return existing
            throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used for a different proposal")
        }
        rejectDuplicate(actor.tenantId, "PURCHASE", request.skuId, null, request.destinationWarehouseId, request.supplierReference)
        return insert(actor, idempotencyKey, "PURCHASE", request.skuId, request.quantity, null, request.destinationWarehouseId, request.supplierReference, request.unitCost, null, request.currency, request.reason, request.recommendationEvidence)
    }

    fun list(actor: TenantAccessContext, status: String?, type: String?): List<ActionProposalView> {
        val sql = StringBuilder("SELECT * FROM action_proposal WHERE tenant_id = ?")
        val args = mutableListOf<Any>(actor.tenantId)
        if (!status.isNullOrBlank()) { sql.append(" AND status = ?"); args += status.uppercase() }
        if (!type.isNullOrBlank()) { sql.append(" AND proposal_type = ?"); args += type.uppercase() }
        if (actor.warehouseIds.isNotEmpty()) {
            val placeholders = actor.warehouseIds.joinToString(",") { "?" }
            sql.append(" AND (source_warehouse_id IN ($placeholders) OR destination_warehouse_id IN ($placeholders))")
            args.addAll(actor.warehouseIds)
            args.addAll(actor.warehouseIds)
        }
        sql.append(" ORDER BY updated_at DESC LIMIT 200")
        return jdbcTemplate.query(sql.toString(), proposalMapper, *args.toTypedArray())
    }

    fun get(actor: TenantAccessContext, proposalId: UUID): ActionProposalView = requireAccessibleProposal(actor, proposalId)

    fun history(actor: TenantAccessContext, proposalId: UUID): List<ProposalHistoryView> {
        requireAccessibleProposal(actor, proposalId)
        return jdbcTemplate.query(
            "SELECT * FROM proposal_status_history WHERE tenant_id = ? AND proposal_id = ? ORDER BY changed_at",
            { rs, _ -> ProposalHistoryView(UUID.fromString(rs.getString("history_id")), UUID.fromString(rs.getString("proposal_id")), rs.getString("from_status"), rs.getString("to_status"), rs.getString("changed_by"), rs.getString("comment"), rs.getTimestamp("changed_at").toLocalDateTime()) },
            actor.tenantId, proposalId
        )
    }

    @Transactional
    fun submit(actor: TenantAccessContext, proposalId: UUID, comment: String?): ActionProposalView {
        val proposal = requireAccessibleProposal(actor, proposalId)
        requireOwnerOrAdmin(actor, proposal)
        transition(actor, proposal, "DRAFT", "PENDING_APPROVAL", comment, submitted = true)
        return requireProposal(actor.tenantId, proposalId)
    }

    @Transactional
    fun approve(actor: TenantAccessContext, proposalId: UUID, comment: String?): ActionProposalView {
        val proposal = requireAccessibleProposal(actor, proposalId)
        if (proposal.createdBy == actor.userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "A proposer cannot approve their own proposal")
        transition(actor, proposal, "PENDING_APPROVAL", "APPROVED", comment, reviewed = true)
        return requireProposal(actor.tenantId, proposalId)
    }

    @Transactional
    fun reject(actor: TenantAccessContext, proposalId: UUID, comment: String?): ActionProposalView {
        if (comment.isNullOrBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection comment is required")
        val proposal = requireAccessibleProposal(actor, proposalId)
        if (proposal.createdBy == actor.userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "A proposer cannot review their own proposal")
        transition(actor, proposal, "PENDING_APPROVAL", "REJECTED", comment, reviewed = true)
        return requireProposal(actor.tenantId, proposalId)
    }

    @Transactional
    fun cancel(actor: TenantAccessContext, proposalId: UUID, comment: String?): ActionProposalView {
        val proposal = requireAccessibleProposal(actor, proposalId)
        requireOwnerOrAdmin(actor, proposal)
        if (proposal.status !in setOf("DRAFT", "PENDING_APPROVAL")) throw ResponseStatusException(HttpStatus.CONFLICT, "Only draft or pending proposals can be cancelled")
        transition(actor, proposal, proposal.status, "CANCELLED", comment)
        return requireProposal(actor.tenantId, proposalId)
    }

    private fun insert(actor: TenantAccessContext, key: String, type: String, skuId: String, quantity: BigDecimal, source: String?, destination: String?, supplier: String?, unitCost: BigDecimal?, transportCost: BigDecimal?, currency: String, reason: String, evidence: String?): ActionProposalView {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """INSERT INTO action_proposal(proposal_id, tenant_id, proposal_type, status, sku_id, quantity, source_warehouse_id, destination_warehouse_id, supplier_reference, unit_cost, transport_cost, currency, reason, recommendation_evidence, idempotency_key, created_by)
               VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            id, actor.tenantId, type, skuId, quantity, source, destination, supplier, unitCost, transportCost, currency.uppercase(), reason, evidence, key, actor.userId
        )
        history(id, actor, null, "DRAFT", "Proposal created")
        return requireProposal(actor.tenantId, id)
    }

    private fun transition(actor: TenantAccessContext, proposal: ActionProposalView, expected: String, target: String, comment: String?, submitted: Boolean = false, reviewed: Boolean = false) {
        if (proposal.status != expected) throw ResponseStatusException(HttpStatus.CONFLICT, "Proposal must be $expected, but is ${proposal.status}")
        val updated = when {
            submitted -> jdbcTemplate.update("UPDATE action_proposal SET status = ?, submitted_by = ?, submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE tenant_id = ? AND proposal_id = ? AND status = ? AND version = ?", target, actor.userId, actor.tenantId, proposal.proposalId, expected, proposal.version)
            reviewed -> jdbcTemplate.update("UPDATE action_proposal SET status = ?, reviewed_by = ?, review_comment = ?, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE tenant_id = ? AND proposal_id = ? AND status = ? AND version = ?", target, actor.userId, comment, actor.tenantId, proposal.proposalId, expected, proposal.version)
            else -> jdbcTemplate.update("UPDATE action_proposal SET status = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE tenant_id = ? AND proposal_id = ? AND status = ? AND version = ?", target, actor.tenantId, proposal.proposalId, expected, proposal.version)
        }
        if (updated != 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Proposal changed concurrently; reload and retry")
        history(proposal.proposalId, actor, expected, target, comment)
    }

    private fun history(id: UUID, actor: TenantAccessContext, from: String?, to: String, comment: String?) {
        jdbcTemplate.update("INSERT INTO proposal_status_history(history_id, proposal_id, tenant_id, from_status, to_status, changed_by, comment) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), id, actor.tenantId, from, to, actor.userId, comment)
    }

    private fun requireOwnerOrAdmin(actor: TenantAccessContext, proposal: ActionProposalView) {
        if (proposal.createdBy != actor.userId && "USER_MANAGE" !in actor.permissions) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the proposer or an administrator may perform this transition")
    }

    private fun rejectDuplicate(tenantId: String, type: String, skuId: String, source: String?, destination: String?, supplier: String?) {
        val count = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM action_proposal WHERE tenant_id = ? AND proposal_type = ? AND sku_id = ?
               AND COALESCE(source_warehouse_id, '') = COALESCE(?, '') AND COALESCE(destination_warehouse_id, '') = COALESCE(?, '')
               AND COALESCE(supplier_reference, '') = COALESCE(?, '') AND status IN ('DRAFT', 'PENDING_APPROVAL')""",
            Long::class.java, tenantId, type, skuId, source, destination, supplier
        ) ?: 0
        if (count > 0) throw ResponseStatusException(HttpStatus.CONFLICT, "A similar open proposal already exists")
    }

    private fun requireIdempotency(key: String) {
        if (key.isBlank() || key.length > 160) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required")
    }

    private fun validateSku(tenantId: String, skuId: String) = validateCount("SELECT COUNT(*) FROM sku WHERE tenant_id = ? AND sku_id = ? AND active = TRUE", tenantId, skuId, "SKU '$skuId' was not found")
    private fun validateWarehouse(tenantId: String, warehouseId: String) = validateCount("SELECT COUNT(*) FROM warehouse WHERE tenant_id = ? AND warehouse_id = ? AND active = TRUE", tenantId, warehouseId, "Warehouse '$warehouseId' was not found")
    private fun requireWarehouseAccess(actor: TenantAccessContext, warehouseId: String) {
        if (actor.warehouseIds.isNotEmpty() && warehouseId !in actor.warehouseIds) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "The caller is not authorized for warehouse '$warehouseId'")
        }
    }
    private fun validateCount(sql: String, tenantId: String, id: String, message: String) { if ((jdbcTemplate.queryForObject(sql, Long::class.java, tenantId, id) ?: 0) == 0L) throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }

    private fun existingByKey(tenantId: String, key: String): ActionProposalView? = jdbcTemplate.query("SELECT * FROM action_proposal WHERE tenant_id = ? AND idempotency_key = ?", proposalMapper, tenantId, key).firstOrNull()
    private fun requireProposal(tenantId: String, id: UUID): ActionProposalView = jdbcTemplate.query("SELECT * FROM action_proposal WHERE tenant_id = ? AND proposal_id = ?", proposalMapper, tenantId, id).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal '$id' was not found")
    private fun requireAccessibleProposal(actor: TenantAccessContext, id: UUID): ActionProposalView {
        val proposal = requireProposal(actor.tenantId, id)
        if (actor.warehouseIds.isNotEmpty() && proposal.sourceWarehouseId !in actor.warehouseIds && proposal.destinationWarehouseId !in actor.warehouseIds) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal '$id' was not found")
        }
        return proposal
    }

    private fun ResultSet.toProposal() = ActionProposalView(
        UUID.fromString(getString("proposal_id")), getString("tenant_id"), getString("proposal_type"), getString("status"), getString("sku_id"), getBigDecimal("quantity"), getString("source_warehouse_id"), getString("destination_warehouse_id"), getString("supplier_reference"), getBigDecimal("unit_cost"), getBigDecimal("transport_cost"), getString("currency"), getString("reason"), getString("recommendation_evidence"), getString("idempotency_key"), getString("created_by"), getString("submitted_by"), getString("reviewed_by"), getString("review_comment"), getTimestamp("created_at").toLocalDateTime(), getTimestamp("updated_at").toLocalDateTime(), getTimestamp("submitted_at")?.toLocalDateTime(), getTimestamp("reviewed_at")?.toLocalDateTime(), getLong("version")
    )
}
