package com.stockflow.district

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DistrictService(private val districtRepository: DistrictRepository) {

    @Transactional(readOnly = true)
    fun getDistrictsByTenant(tenantId: String): List<DistrictRegistry> {
        return districtRepository.findByTenantId(tenantId)
    }
}
