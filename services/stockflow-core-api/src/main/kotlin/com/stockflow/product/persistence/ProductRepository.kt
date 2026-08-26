package com.stockflow.product.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<ProductEntity, String> {
    fun findAllByTenantIdOrderByProductName(tenantId: String): List<ProductEntity>
    fun findByProductIdAndTenantId(productId: String, tenantId: String): ProductEntity?
    fun countByTenantIdAndActiveTrue(tenantId: String): Long
}
