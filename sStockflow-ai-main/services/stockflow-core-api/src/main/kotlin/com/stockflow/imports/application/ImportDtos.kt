package com.stockflow.imports.application

import com.stockflow.imports.persistence.ImportMode
import com.stockflow.imports.persistence.ImportStatus
import java.time.LocalDateTime
import java.util.UUID

data class ImportJobView(
    val importJobId: UUID,
    val tenantId: String,
    val importType: String,
    val fileName: String,
    val fileSha256: String,
    val importMode: ImportMode,
    val status: ImportStatus,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val totalRows: Long,
    val acceptedRows: Long,
    val rejectedRows: Long,
    val ignoredRows: Long,
    val message: String?
)

data class ImportErrorView(
    val importErrorId: UUID,
    val fileName: String,
    val rowNumber: Long,
    val errorCode: String,
    val fieldName: String?,
    val rejectedValue: String?,
    val message: String
)

data class CsvRow(val rowNumber: Long, val values: Map<String, String>)
