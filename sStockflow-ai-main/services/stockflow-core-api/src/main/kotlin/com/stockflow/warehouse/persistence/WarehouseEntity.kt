package com.stockflow.warehouse.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "warehouse")
open class WarehouseEntity(
    @Id
    @Column(name = "warehouse_id", nullable = false, length = 64)
    open var warehouseId: String = "",

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "warehouse_name", nullable = false, length = 200)
    open var warehouseName: String = "",

    @Column(name = "city", nullable = false, length = 100)
    open var city: String = "",

    @Column(name = "state", nullable = false, length = 100)
    open var state: String = "",

    @Column(name = "country", nullable = false, length = 100)
    open var country: String = "India",

    @Column(name = "latitude", precision = 10, scale = 7)
    open var latitude: BigDecimal? = null,

    @Column(name = "longitude", precision = 10, scale = 7)
    open var longitude: BigDecimal? = null,

    @Column(name = "capacity_units", nullable = false)
    open var capacityUnits: Long = 0,

    @Column(name = "cold_chain_available", nullable = false)
    open var coldChainAvailable: Boolean = false,

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)
