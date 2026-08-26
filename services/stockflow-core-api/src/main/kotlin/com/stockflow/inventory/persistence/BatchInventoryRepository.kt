package com.stockflow.inventory.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BatchInventoryRepository : JpaRepository<BatchInventoryEntity, UUID> {
    fun findAllByTenantIdOrderByExpiryDateAsc(tenantId: String): List<BatchInventoryEntity>
    fun findAllByTenantIdAndWarehouseIdOrderByExpiryDateAsc(
        tenantId: String,
        warehouseId: String
    ): List<BatchInventoryEntity>
    fun findAllByTenantIdAndSkuIdOrderByExpiryDateAsc(
        tenantId: String,
        skuId: String
    ): List<BatchInventoryEntity>
    fun findAllByTenantIdAndWarehouseIdAndSkuIdOrderByExpiryDateAsc(
        tenantId: String,
        warehouseId: String,
        skuId: String
    ): List<BatchInventoryEntity>
    fun findBySnapshotDateAndTenantIdAndWarehouseIdAndSkuIdAndBatchNumber(
        snapshotDate: java.time.LocalDate,
        tenantId: String,
        warehouseId: String,
        skuId: String,
        batchNumber: String
    ): BatchInventoryEntity?
    fun countByTenantId(tenantId: String): Long
}
