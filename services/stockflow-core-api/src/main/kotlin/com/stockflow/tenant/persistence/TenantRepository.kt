package com.stockflow.tenant.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<TenantEntity, String> {
    fun findByTenantIdAndActiveTrue(tenantId: String): TenantEntity?
}
