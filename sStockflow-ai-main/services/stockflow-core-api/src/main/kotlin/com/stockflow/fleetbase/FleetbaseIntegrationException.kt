package com.stockflow.fleetbase

import org.springframework.http.HttpStatus

class FleetbaseIntegrationException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)
