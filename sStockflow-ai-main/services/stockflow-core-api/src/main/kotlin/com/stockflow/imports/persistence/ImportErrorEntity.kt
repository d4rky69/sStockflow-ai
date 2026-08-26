package com.stockflow.imports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "import_error")
open class ImportErrorEntity(
    @Id
    @Column(name = "import_error_id", nullable = false)
    open var importErrorId: UUID = UUID.randomUUID(),

    @Column(name = "import_job_id", nullable = false)
    open var importJobId: UUID = UUID.randomUUID(),

    @Column(name = "file_name", nullable = false, length = 255)
    open var fileName: String = "",

    @Column(name = "row_number", nullable = false)
    open var rowNumber: Long = 0,

    @Column(name = "error_code", nullable = false, length = 80)
    open var errorCode: String = "VALIDATION_ERROR",

    @Column(name = "field_name", length = 120)
    open var fieldName: String? = null,

    @Column(name = "rejected_value", length = 500)
    open var rejectedValue: String? = null,

    @Column(name = "message", nullable = false, length = 1000)
    open var message: String = "",

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now()
)
