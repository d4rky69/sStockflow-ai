package com.stockflow.product.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SkuRepository : JpaRepository<SkuEntity, String> {
    fun findAllByTenantIdAndActiveTrueOrderBySkuName(tenantId: String): List<SkuEntity>
    fun findBySkuIdAndTenantId(skuId: String, tenantId: String): SkuEntity?
    fun findBySkuIdAndTenantIdAndActiveTrue(skuId: String, tenantId: String): SkuEntity?
    fun countByTenantIdAndActiveTrue(tenantId: String): Long
}
