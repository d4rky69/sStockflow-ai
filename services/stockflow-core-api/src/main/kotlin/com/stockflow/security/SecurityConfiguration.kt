package com.stockflow.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "stockflow.security", name = ["enabled"], havingValue = "true")
class SecurityConfiguration(
    private val tenantAuthorizationFilter: TenantAuthorizationFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
            it.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/integrations/fleetbase/webhooks").permitAll()
            it.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
            it.anyRequest().authenticated()
        }
        .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        .addFilterAfter(tenantAuthorizationFilter, BearerTokenAuthenticationFilter::class.java)
        .build()
}

@Configuration
@ConditionalOnProperty(prefix = "stockflow.security", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class DevelopmentSecurityConfiguration {
    @Bean
    fun developmentSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()
}
