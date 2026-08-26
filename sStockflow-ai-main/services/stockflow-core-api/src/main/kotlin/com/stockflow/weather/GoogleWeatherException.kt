package com.stockflow.weather

import org.springframework.http.HttpStatus

class GoogleWeatherException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)
