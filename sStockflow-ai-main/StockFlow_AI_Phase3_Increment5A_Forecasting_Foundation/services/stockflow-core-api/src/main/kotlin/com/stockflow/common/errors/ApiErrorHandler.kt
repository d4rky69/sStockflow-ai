package com.stockflow.common.errors

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(ResourceNotFoundException::class)
    fun notFound(error: ResourceNotFoundException): ResponseEntity<ApiError> =
        response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", error.message ?: "Resource not found")

    @ExceptionHandler(InvalidTenantException::class)
    fun invalidTenant(error: InvalidTenantException): ResponseEntity<ApiError> =
        response(HttpStatus.FORBIDDEN, "INVALID_TENANT", error.message ?: "Tenant is invalid")


    @ExceptionHandler(InvalidImportException::class)
    fun invalidImport(error: InvalidImportException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "INVALID_IMPORT", error.message ?: "Import package is invalid")

    @ExceptionHandler(InvalidForecastException::class)
    fun invalidForecast(error: InvalidForecastException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "INVALID_FORECAST", error.message ?: "Forecast request is invalid")

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(error: MissingRequestHeaderException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Required header '${error.headerName}' is missing")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(error: MethodArgumentNotValidException): ResponseEntity<ApiError> =
        response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            error.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        )

    private fun response(status: HttpStatus, code: String, message: String): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(
            ApiError(
                timestamp = Instant.now(),
                status = status.value(),
                code = code,
                message = message
            )
        )
}

data class ApiError(
    val timestamp: Instant,
    val status: Int,
    val code: String,
    val message: String
)
