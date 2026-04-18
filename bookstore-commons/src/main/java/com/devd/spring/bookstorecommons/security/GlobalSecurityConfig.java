package com.devd.spring.bookstorecommons.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Global security config shared across all services.
 *
 * Spring Security 6 removed WebSecurityConfigurerAdapter.
 * Configuration is now done via @Bean methods instead of extending an adapter.
 *
 * - @EnableMethodSecurity replaces @EnableGlobalMethodSecurity(prePostEnabled=true)
 * - BCryptPasswordEncoder registered as PasswordEncoder bean
 *
 * Note: AuthenticationManager is NOT defined here - each service that needs it
 * (e.g. account-service) defines its own with the appropriate UserDetailsService.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class GlobalSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
