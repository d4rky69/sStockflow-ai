package com.stockflow.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class TenantAccessContext(
    val userId: String,
    val email: String?,
    val tenantId: String,
    val roleCode: String,
    val permissions: Set<String>,
    val warehouseIds: Set<String>
)

data class TenantMemberView(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val roleCode: String,
    val active: Boolean,
    val warehouseIds: Set<String>
)

@Service
class TenantSecurityService(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${stockflow.security.bootstrap-first-user-admin:false}")
    private val bootstrapFirstUserAdmin: Boolean
) {
    @Transactional
    fun developmentContext(tenantId: String, userId: String, email: String?): TenantAccessContext {
        require(tenantId.isNotBlank()) { "X-Tenant-ID is required" }
        require(userId.isNotBlank()) { "X-StockFlow-User-ID is required" }
        val tenantExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenant WHERE tenant_id = ? AND active = TRUE",
            Long::class.java,
            tenantId
        ) ?: 0L
        if (tenantExists == 0L) throw TenantAccessDeniedException("Tenant '$tenantId' is not available")

        val userExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE user_id = ?",
            Long::class.java,
            userId
        ) ?: 0L
        if (userExists == 0L) {
            jdbcTemplate.update(
                "INSERT INTO app_user(user_id, email, display_name) VALUES (?, ?, ?)",
                userId,
                email,
                email?.substringBefore('@') ?: "Local prototype user"
            )
        } else if (!email.isNullOrBlank()) {
            jdbcTemplate.update(
                "UPDATE app_user SET email = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                email,
                userId
            )
        }

        // Local mode deliberately grants the complete permission set. Production mode never
        // calls this path and continues to enforce validated JWT tenant memberships and RBAC.
        return TenantAccessContext(
            userId = userId,
            email = email,
            tenantId = tenantId,
            roleCode = "LOCAL_ADMIN",
            permissions = jdbcTemplate.query(
                "SELECT permission_code FROM permission_definition",
                { rs, _ -> rs.getString("permission_code") }
            ).toSet(),
            warehouseIds = emptySet()
        )
    }

    @Transactional
    fun authorize(jwt: Jwt, tenantId: String, requiredPermission: String?): TenantAccessContext {
        require(tenantId.isNotBlank()) { "X-Tenant-ID is required" }
        val userId = jwt.subject ?: throw TenantAccessDeniedException("The access token has no subject")
        val email = jwt.getClaimAsString("email")
        var role = membershipRole(tenantId, userId)
        if (role == null && bootstrapFirstUserAdmin && membershipCount(tenantId) == 0L) {
            bootstrapAdministrator(userId, email, tenantId)
            role = "ADMIN"
            audit(tenantId, userId, "FIRST_ADMIN_BOOTSTRAPPED", "/api/v1/security", "SUCCESS", email)
        }
        if (role == null) {
            audit(tenantId, userId, "TENANT_ACCESS_DENIED", null, "DENIED", "No active tenant membership")
            throw TenantAccessDeniedException("The authenticated user is not a member of tenant '$tenantId'")
        }
        val permissions = permissions(role)
        if (requiredPermission != null && requiredPermission !in permissions) {
            audit(tenantId, userId, "PERMISSION_DENIED", null, "DENIED", "$role lacks $requiredPermission")
            throw TenantAccessDeniedException("Role '$role' does not have permission '$requiredPermission'")
        }
        return TenantAccessContext(userId, email, tenantId, role, permissions, warehouseIds(tenantId, userId))
    }

    fun context(jwt: Jwt, tenantId: String): TenantAccessContext = authorize(jwt, tenantId, null)

    fun members(tenantId: String): List<TenantMemberView> = jdbcTemplate.query(
        """
        SELECT u.user_id, u.email, u.display_name, m.role_code, m.active
        FROM tenant_membership m
        JOIN app_user u ON u.user_id = m.user_id
        WHERE m.tenant_id = ?
        ORDER BY u.email, u.user_id
        """.trimIndent(),
        { rs, _ -> TenantMemberView(
            userId = rs.getString("user_id"),
            email = rs.getString("email"),
            displayName = rs.getString("display_name"),
            roleCode = rs.getString("role_code"),
            active = rs.getBoolean("active"),
            warehouseIds = warehouseIds(tenantId, rs.getString("user_id"))
        ) }, tenantId
    )

    @Transactional
    fun upsertMember(actor: TenantAccessContext, userId: String, email: String?, displayName: String?, roleCode: String, active: Boolean, warehouseIds: Set<String>): TenantMemberView {
        require(userId.isNotBlank()) { "userId is required" }
        val validRole = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_definition WHERE role_code = ? AND active = TRUE", Long::class.java, roleCode) ?: 0
        require(validRole == 1L) { "Unknown or inactive role '$roleCode'" }
        val invalidWarehouseCount = if (warehouseIds.isEmpty()) 0 else warehouseIds.count { warehouseId ->
            (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse WHERE tenant_id = ? AND warehouse_id = ? AND active = TRUE", Long::class.java, actor.tenantId, warehouseId) ?: 0) == 0L
        }
        require(invalidWarehouseCount == 0) { "One or more warehouses do not belong to tenant '${actor.tenantId}'" }
        val userExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE user_id = ?", Long::class.java, userId) ?: 0
        if (userExists == 0L) {
            jdbcTemplate.update("INSERT INTO app_user(user_id, email, display_name) VALUES (?, ?, ?)", userId, email, displayName)
        } else {
            jdbcTemplate.update("UPDATE app_user SET email = ?, display_name = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", email, displayName, userId)
        }
        val membershipExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant_membership WHERE tenant_id = ? AND user_id = ?", Long::class.java, actor.tenantId, userId) ?: 0
        if (membershipExists == 0L) {
            jdbcTemplate.update("INSERT INTO tenant_membership(tenant_id, user_id, role_code, active) VALUES (?, ?, ?, ?)", actor.tenantId, userId, roleCode, active)
        } else {
            jdbcTemplate.update("UPDATE tenant_membership SET role_code = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND user_id = ?", roleCode, active, actor.tenantId, userId)
        }
        jdbcTemplate.update("DELETE FROM warehouse_access WHERE tenant_id = ? AND user_id = ?", actor.tenantId, userId)
        warehouseIds.forEach { warehouseId -> jdbcTemplate.update("INSERT INTO warehouse_access(tenant_id, user_id, warehouse_id) VALUES (?, ?, ?)", actor.tenantId, userId, warehouseId) }
        audit(actor.tenantId, actor.userId, "MEMBERSHIP_UPDATED", "/api/v1/security/memberships/$userId", "SUCCESS", "role=$roleCode active=$active")
        return TenantMemberView(userId, email, displayName, roleCode, active, warehouseIds)
    }

    private fun membershipRole(tenantId: String, userId: String): String? = jdbcTemplate.query(
        "SELECT role_code FROM tenant_membership WHERE tenant_id = ? AND user_id = ? AND active = TRUE",
        { rs, _ -> rs.getString("role_code") }, tenantId, userId
    ).firstOrNull()

    private fun membershipCount(tenantId: String): Long = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tenant_membership WHERE tenant_id = ? AND active = TRUE", Long::class.java, tenantId
    ) ?: 0L

    private fun permissions(role: String): Set<String> = jdbcTemplate.query(
        "SELECT permission_code FROM role_permission WHERE role_code = ?",
        { rs, _ -> rs.getString("permission_code") }, role
    ).toSet()

    private fun warehouseIds(tenantId: String, userId: String): Set<String> = jdbcTemplate.query(
        "SELECT warehouse_id FROM warehouse_access WHERE tenant_id = ? AND user_id = ?",
        { rs, _ -> rs.getString("warehouse_id") }, tenantId, userId
    ).toSet()

    private fun bootstrapAdministrator(userId: String, email: String?, tenantId: String) {
        val exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE user_id = ?", Long::class.java, userId) ?: 0
        if (exists == 0L) {
            jdbcTemplate.update("INSERT INTO app_user(user_id, email, display_name) VALUES (?, ?, ?)", userId, email, email?.substringBefore('@'))
        }
        jdbcTemplate.update("INSERT INTO tenant_membership(tenant_id, user_id, role_code) VALUES (?, ?, 'ADMIN')", tenantId, userId)
    }

    fun audit(tenantId: String?, userId: String?, type: String, resource: String?, outcome: String, details: String?) {
        jdbcTemplate.update(
            "INSERT INTO security_audit_event(audit_event_id, tenant_id, user_id, event_type, resource, outcome, details) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), tenantId, userId, type, resource, outcome, details
        )
    }
}

class TenantAccessDeniedException(message: String) : RuntimeException(message)
