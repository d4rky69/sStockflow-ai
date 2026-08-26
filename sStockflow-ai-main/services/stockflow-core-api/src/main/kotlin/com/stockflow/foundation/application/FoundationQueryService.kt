package com.stockflow.foundation.application

import com.stockflow.common.errors.InvalidTenantException
import com.stockflow.common.errors.ResourceNotFoundException
import com.stockflow.inventory.persistence.BatchInventoryRepository
import com.stockflow.product.persistence.ProductRepository
import com.stockflow.product.persistence.SkuRepository
import com.stockflow.tenant.persistence.TenantEntity
import com.stockflow.tenant.persistence.TenantRepository
import com.stockflow.warehouse.persistence.WarehouseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FoundationQueryService(
    private val tenantRepository: TenantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val productRepository: ProductRepository,
    private val skuRepository: SkuRepository,
    private val batchInventoryRepository: BatchInventoryRepository
) {
    fun requireTenant(tenantId: String): TenantEntity =
        tenantRepository.findByTenantIdAndActiveTrue(tenantId)
            ?: throw InvalidTenantException("Tenant '$tenantId' does not exist or is inactive")

    fun summary(tenantId: String): FoundationSummary {
        val tenant = requireTenant(tenantId)
        return FoundationSummary(
            tenant = TenantView(
                tenantId = tenant.tenantId,
                tenantName = tenant.tenantName,
                vertical = tenant.vertical,
                currency = tenant.currency,
                timezone = tenant.timezone
            ),
            warehouseCount = warehouseRepository.countByTenantIdAndActiveTrue(tenantId),
            productCount = productRepository.countByTenantIdAndActiveTrue(tenantId),
            skuCount = skuRepository.countByTenantIdAndActiveTrue(tenantId),
            batchCount = batchInventoryRepository.countByTenantId(tenantId)
        )
    }

    fun warehouses(tenantId: String): List<WarehouseView> {
        requireTenant(tenantId)
        return warehouseRepository.findAllByTenantIdAndActiveTrueOrderByWarehouseName(tenantId).map {
            WarehouseView(
                warehouseId = it.warehouseId,
                warehouseName = it.warehouseName,
                city = it.city,
                state = it.state,
                country = it.country,
                capacityUnits = it.capacityUnits,
                coldChainAvailable = it.coldChainAvailable
            )
        }
    }

    fun warehouse(tenantId: String, warehouseId: String): WarehouseView {
        requireTenant(tenantId)
        val warehouse = warehouseRepository.findByWarehouseIdAndTenantIdAndActiveTrue(warehouseId, tenantId)
            ?: throw ResourceNotFoundException("Warehouse '$warehouseId' was not found in tenant '$tenantId'")
        return WarehouseView(
            warehouseId = warehouse.warehouseId,
            warehouseName = warehouse.warehouseName,
            city = warehouse.city,
            state = warehouse.state,
            country = warehouse.country,
            capacityUnits = warehouse.capacityUnits,
            coldChainAvailable = warehouse.coldChainAvailable
        )
    }

    fun skus(tenantId: String): List<SkuView> {
        requireTenant(tenantId)
        return skuRepository.findAllByTenantIdAndActiveTrueOrderBySkuName(tenantId).map {
            SkuView(
                skuId = it.skuId,
                productId = it.productId,
                skuName = it.skuName,
                baseUom = it.baseUom,
                unitCost = it.unitCost,
                sellingPrice = it.sellingPrice,
                currency = it.currency,
                minimumSafetyStock = it.minimumSafetyStock,
                reorderMultiple = it.reorderMultiple,
                defaultShelfLifeDays = it.defaultShelfLifeDays,
                fefoRequired = it.fefoRequired,
                demandProfile = it.demandProfile
            )
        }
    }

    fun batches(tenantId: String, warehouseId: String?, skuId: String?): List<BatchInventoryView> {
        requireTenant(tenantId)
        val batches = when {
            warehouseId != null && skuId != null ->
                batchInventoryRepository.findAllByTenantIdAndWarehouseIdAndSkuIdOrderByExpiryDateAsc(
                    tenantId, warehouseId, skuId
                )
            warehouseId != null ->
                batchInventoryRepository.findAllByTenantIdAndWarehouseIdOrderByExpiryDateAsc(tenantId, warehouseId)
            skuId != null ->
                batchInventoryRepository.findAllByTenantIdAndSkuIdOrderByExpiryDateAsc(tenantId, skuId)
            else -> batchInventoryRepository.findAllByTenantIdOrderByExpiryDateAsc(tenantId)
        }
        return batches.map {
            BatchInventoryView(
                batchInventoryId = it.batchInventoryId,
                snapshotDate = it.snapshotDate,
                warehouseId = it.warehouseId,
                skuId = it.skuId,
                batchNumber = it.batchNumber,
                manufactureDate = it.manufactureDate,
                expiryDate = it.expiryDate,
                availableQuantity = it.availableQuantity,
                reservedQuantity = it.reservedQuantity,
                blockedQuantity = it.blockedQuantity,
                usableQuantity = it.usableQuantity(),
                unitCost = it.unitCost,
                currency = it.currency,
                storageConditionCode = it.storageConditionCode,
                lastMovementAt = it.lastMovementAt
            )
        }
    }
}
