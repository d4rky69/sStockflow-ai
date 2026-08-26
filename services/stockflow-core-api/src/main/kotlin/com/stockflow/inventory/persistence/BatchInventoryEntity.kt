package com.stockflow.inventory.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "batch_inventory")
open class BatchInventoryEntity(
    @Id
    @Column(name = "batch_inventory_id", nullable = false)
    open var batchInventoryId: UUID = UUID.randomUUID(),

    @Column(name = "snapshot_date", nullable = false)
    open var snapshotDate: LocalDate = LocalDate.now(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "warehouse_id", nullable = false, length = 64)
    open var warehouseId: String = "",

    @Column(name = "sku_id", nullable = false, length = 80)
    open var skuId: String = "",

    @Column(name = "batch_number", nullable = false, length = 100)
    open var batchNumber: String = "",

    @Column(name = "manufacture_date")
    open var manufactureDate: LocalDate? = null,

    @Column(name = "expiry_date")
    open var expiryDate: LocalDate? = null,

    @Column(name = "available_quantity", nullable = false)
    open var availableQuantity: Long = 0,

    @Column(name = "reserved_quantity", nullable = false)
    open var reservedQuantity: Long = 0,

    @Column(name = "blocked_quantity", nullable = false)
    open var blockedQuantity: Long = 0,

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    open var unitCost: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String = "INR",

    @Column(name = "storage_condition_code", nullable = false, length = 40)
    open var storageConditionCode: String = "AMBIENT",

    @Column(name = "last_movement_at")
    open var lastMovementAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun usableQuantity(): Long = availableQuantity - reservedQuantity - blockedQuantity
}
