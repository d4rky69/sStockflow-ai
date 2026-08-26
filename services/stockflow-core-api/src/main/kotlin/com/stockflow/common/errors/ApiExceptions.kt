package com.stockflow.common.errors

class ResourceNotFoundException(message: String) : RuntimeException(message)
class InvalidTenantException(message: String) : RuntimeException(message)

class InvalidImportException(message: String) : RuntimeException(message)

class InvalidForecastException(message: String) : RuntimeException(message)
