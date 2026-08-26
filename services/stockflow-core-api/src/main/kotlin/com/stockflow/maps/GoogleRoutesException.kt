package com.stockflow.maps

import org.springframework.http.HttpStatus

class GoogleRoutesException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)
