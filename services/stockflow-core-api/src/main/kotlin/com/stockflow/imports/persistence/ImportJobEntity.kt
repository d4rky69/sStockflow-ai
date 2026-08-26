package com.stockflow.imports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "import_job")
open class ImportJobEntity(
    @Id
    @Column(name = "import_job_id", nullable = false)
    open var importJobId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "import_type", nullable = false, length = 50)
    open var importType: String = "SYNTHETIC_FOUNDATION",

    @Column(name = "file_name", nullable = false, length = 255)
    open var fileName: String = "",

    @Column(name = "file_sha256", nullable = false, length = 64)
    open var fileSha256: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "import_mode", nullable = false, length = 30)
    open var importMode: ImportMode = ImportMode.VALIDATE_ONLY,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    open var status: ImportStatus = ImportStatus.RUNNING,

    @Column(name = "started_at", nullable = false)
    open var startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    open var completedAt: LocalDateTime? = null,

    @Column(name = "total_rows", nullable = false)
    open var totalRows: Long = 0,

    @Column(name = "accepted_rows", nullable = false)
    open var acceptedRows: Long = 0,

    @Column(name = "rejected_rows", nullable = false)
    open var rejectedRows: Long = 0,

    @Column(name = "ignored_rows", nullable = false)
    open var ignoredRows: Long = 0,

    @Column(name = "message", length = 1000)
    open var message: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class ImportMode { VALIDATE_ONLY, UPSERT }
enum class ImportStatus { RUNNING, VALIDATED, COMPLETED, COMPLETED_WITH_ERRORS, REJECTED, FAILED }
