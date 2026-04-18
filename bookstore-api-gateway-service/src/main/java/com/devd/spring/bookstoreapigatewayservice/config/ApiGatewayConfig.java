package com.devd.spring.bookstoreapigatewayservice.config;

import org.springframework.context.annotation.Configuration;

/**
 * API Gateway configuration.
 *
 * Zuul filter beans (PreFilter, PostFilter, RouteFilter) removed —
 * Spring Cloud Gateway MVC handles routing via application.yml routes.
 *
 * Brave Sampler removed — tracing now configured via
 * management.tracing.sampling.probability in application.yml.
 */
@Configuration
public class ApiGatewayConfig {
}
