package com.stockflow.district

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DistrictRepository : JpaRepository<DistrictRegistry, String> {
    fun findByTenantId(tenantId: String): List<DistrictRegistry>
}
