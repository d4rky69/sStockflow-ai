package com.stockflow.tenant.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tenant")
open class TenantEntity(
    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "tenant_name", nullable = false, length = 200)
    open var tenantName: String = "",

    @Column(name = "vertical", nullable = false, length = 50)
    open var vertical: String = "",

    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String = "INR",

    @Column(name = "timezone", nullable = false, length = 80)
    open var timezone: String = "Asia/Kolkata",

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)
