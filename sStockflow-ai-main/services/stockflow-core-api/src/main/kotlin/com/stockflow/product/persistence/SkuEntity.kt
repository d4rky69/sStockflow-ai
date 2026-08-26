package com.stockflow.product.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "sku")
open class SkuEntity(
    @Id
    @Column(name = "sku_id", nullable = false, length = 80)
    open var skuId: String = "",

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "product_id", nullable = false, length = 80)
    open var productId: String = "",

    @Column(name = "sku_name", nullable = false, length = 200)
    open var skuName: String = "",

    @Column(name = "brand", length = 120)
    open var brand: String? = null,

    @Column(name = "pack_size", length = 80)
    open var packSize: String? = null,

    @Column(name = "base_uom", nullable = false, length = 30)
    open var baseUom: String = "UNIT",

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    open var unitCost: BigDecimal = BigDecimal.ZERO,

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    open var sellingPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String = "INR",

    @Column(name = "minimum_safety_stock", nullable = false)
    open var minimumSafetyStock: Long = 0,

    @Column(name = "reorder_multiple", nullable = false)
    open var reorderMultiple: Long = 1,

    @Column(name = "default_shelf_life_days")
    open var defaultShelfLifeDays: Int? = null,

    @Column(name = "fefo_required", nullable = false)
    open var fefoRequired: Boolean = true,

    @Column(name = "demand_profile", nullable = false, length = 40)
    open var demandProfile: String = "STABLE",

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)
