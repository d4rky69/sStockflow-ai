package com.stockflow.common.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfiguration(
    @Value("\${STOCKFLOW_CORS_ALLOWED_ORIGINS:http://localhost:4200}")
    private val allowedOriginsValue: String
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOrigins = allowedOriginsValue
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()

        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }
}