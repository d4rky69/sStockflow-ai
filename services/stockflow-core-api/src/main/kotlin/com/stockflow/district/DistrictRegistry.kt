package com.stockflow.district

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "district_registry")
data class DistrictRegistry(
    @Id
    @Column(name = "district_id")
    val districtId: String,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "status", nullable = false)
    val status: String,

    @Column(name = "source", nullable = false)
    val source: String,

    @Column(name = "extracted_at", nullable = false)
    val extractedAt: OffsetDateTime,

    @Column(name = "valid_until", nullable = false)
    val validUntil: OffsetDateTime,

    @Column(name = "confidence_score", nullable = false)
    val confidenceScore: BigDecimal,

    @Column(name = "geometry_json", nullable = false)
    val geometryJson: String
)
