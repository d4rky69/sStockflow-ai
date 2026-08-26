package com.stockflow.product.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "product")
open class ProductEntity(
    @Id
    @Column(name = "product_id", nullable = false, length = 80)
    open var productId: String = "",

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "product_name", nullable = false, length = 200)
    open var productName: String = "",

    @Column(name = "vertical", nullable = false, length = 50)
    open var vertical: String = "",

    @Column(name = "category", nullable = false, length = 80)
    open var category: String = "",

    @Column(name = "criticality", nullable = false, length = 20)
    open var criticality: String = "MEDIUM",

    @Column(name = "shelf_life_controlled", nullable = false)
    open var shelfLifeControlled: Boolean = true,

    @Column(name = "cold_chain_required", nullable = false)
    open var coldChainRequired: Boolean = false,

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)
