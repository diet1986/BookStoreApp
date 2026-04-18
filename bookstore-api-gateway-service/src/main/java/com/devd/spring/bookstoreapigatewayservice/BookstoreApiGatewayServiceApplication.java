package com.devd.spring.bookstoreapigatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Service using Spring Cloud Gateway MVC (Servlet-based).
 *
 * Replaces Netflix Zuul which was discontinued in Spring Cloud 2021.
 * Spring Cloud Gateway MVC was introduced Dec 2023 as a non-reactive
 * alternative that works with the existing Spring MVC / Servlet stack.
 *
 * Routes are configured in application.yml under spring.cloud.gateway.mvc.routes
 */
@SpringBootApplication
@EnableDiscoveryClient
public class BookstoreApiGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookstoreApiGatewayServiceApplication.class, args);
    }
}
