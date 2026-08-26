package com.stockflow.imports.api

import com.stockflow.imports.application.ImportErrorView
import com.stockflow.imports.application.ImportJobView
import com.stockflow.imports.application.SyntheticFoundationImportService
import com.stockflow.imports.application.SyntheticSalesImportService
import com.stockflow.imports.persistence.ImportMode
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/imports")
class ImportController(
    private val importService: SyntheticFoundationImportService,
    private val salesImportService: SyntheticSalesImportService
) {
    @PostMapping(
        "/synthetic-foundation",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun importSyntheticFoundation(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(defaultValue = "VALIDATE_ONLY") mode: ImportMode,
        @RequestParam(defaultValue = "true") strict: Boolean
    ): ImportJobView = importService.importPackage(tenantId, file, mode, strict)

    @PostMapping(
        "/synthetic-sales",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun importSyntheticSales(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(defaultValue = "VALIDATE_ONLY") mode: ImportMode,
        @RequestParam(defaultValue = "true") strict: Boolean
    ): ImportJobView = salesImportService.importPackage(tenantId, file, mode, strict)

    @GetMapping
    fun recentJobs(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): List<ImportJobView> = importService.recentJobs(tenantId)

    @GetMapping("/{importJobId}")
    fun job(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable importJobId: UUID
    ): ImportJobView = importService.job(tenantId, importJobId)

    @GetMapping("/{importJobId}/errors")
    fun errors(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable importJobId: UUID
    ): List<ImportErrorView> = importService.errors(tenantId, importJobId)
}
