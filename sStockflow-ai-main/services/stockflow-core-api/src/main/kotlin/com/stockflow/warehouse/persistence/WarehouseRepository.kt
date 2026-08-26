package com.stockflow.warehouse.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WarehouseRepository : JpaRepository<WarehouseEntity, String> {
    fun findAllByTenantIdAndActiveTrueOrderByWarehouseName(tenantId: String): List<WarehouseEntity>
    fun findByWarehouseIdAndTenantId(warehouseId: String, tenantId: String): WarehouseEntity?
    fun findByWarehouseIdAndTenantIdAndActiveTrue(warehouseId: String, tenantId: String): WarehouseEntity?
    fun countByTenantIdAndActiveTrue(tenantId: String): Long
}
