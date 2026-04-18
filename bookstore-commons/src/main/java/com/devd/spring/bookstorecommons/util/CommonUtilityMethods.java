package com.devd.spring.bookstorecommons.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

/**
 * Utility methods for extracting user info from JWT tokens.
 *
 * Migrated from Spring Security OAuth2 (EOL) to Spring Security 6 JWT.
 * OAuth2Authentication replaced by JwtAuthenticationToken.
 * user_id and user_name are custom claims added by the Authorization Server.
 */
public class CommonUtilityMethods {

    /**
     * Extracts user_id from the JWT token claims.
     * The user_id claim is added by AuthorizationServerConfig.jwtTokenCustomizer()
     * in the account-service.
     */
    public static String getUserIdFromToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwt.getClaimAsString("user_id");
        }
        throw new IllegalArgumentException(
            "Authentication is not a JwtAuthenticationToken: " + authentication.getClass());
    }

    /**
     * Extracts user_name (subject) from the JWT token claims.
     */
    public static String getUserNameFromToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            // 'sub' is the standard JWT subject claim — Spring Authorization Server
            // sets this to the username
            String userName = jwt.getClaimAsString("user_name");
            if (userName == null) {
                userName = jwt.getSubject();
            }
            return userName;
        }
        throw new IllegalArgumentException(
            "Authentication is not a JwtAuthenticationToken: " + authentication.getClass());
    }
}
