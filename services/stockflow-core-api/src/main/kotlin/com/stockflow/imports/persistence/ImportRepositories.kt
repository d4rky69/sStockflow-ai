package com.stockflow.imports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ImportJobRepository : JpaRepository<ImportJobEntity, UUID> {
    fun findByImportJobIdAndTenantId(importJobId: UUID, tenantId: String): ImportJobEntity?
    fun findTop20ByTenantIdOrderByStartedAtDesc(tenantId: String): List<ImportJobEntity>
}

interface ImportErrorRepository : JpaRepository<ImportErrorEntity, UUID> {
    fun findAllByImportJobIdOrderByRowNumberAsc(importJobId: UUID): List<ImportErrorEntity>
}
