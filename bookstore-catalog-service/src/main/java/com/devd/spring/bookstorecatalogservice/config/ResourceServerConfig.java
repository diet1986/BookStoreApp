package com.devd.spring.bookstorecatalogservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security config for catalog-service.
 * @Order(2) takes priority over GlobalResourceServerConfig @Order(3) from commons.
 * Public read access to products, reviews, images and categories.
 * Write operations require authentication.
 */
@Configuration
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll()
                // Public read access - browsing catalog doesn't require login
                .requestMatchers(HttpMethod.GET, "/product", "/product/**", "/products", "/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/review", "/review/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/image", "/image/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/productCategories", "/productCategories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/productCategory", "/productCategory/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
