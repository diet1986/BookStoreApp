package com.devd.spring.bookstoreaccountservice.config;

import com.devd.spring.bookstoreaccountservice.repository.UserRepository;
import com.devd.spring.bookstoreaccountservice.repository.dao.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Authorization Server configuration.
 * Replaces the legacy AuthorizationServerConfigurerAdapter from
 * spring-security-oauth2 (EOL May 2022).
 *
 * Note: OAuth 2.1 (Spring Authorization Server 1.x) removed the password grant type.
 * We support it via a custom grant authenticator to maintain backward compatibility
 * with the existing React frontend.
 */
@Configuration
public class AuthorizationServerConfig {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${security.jwt.key-store}")
    private Resource keyStore;

    @Value("${security.jwt.key-store-password}")
    private String keyStorePassword;

    @Value("${security.jwt.key-pair-alias}")
    private String keyPairAlias;

    @Value("${security.jwt.key-pair-password}")
    private String keyPairPassword;

    @Value("${OAUTH2_ISSUER_URI:http://localhost:4001}")
    private String issuerUri;

    /**
     * Authorization server security filter chain.
     * Highest priority - handles /oauth2/** endpoints.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        PasswordGrantAuthenticationProvider passwordGrantProvider =
            new PasswordGrantAuthenticationProvider(authenticationManager, userRepository);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults())
            .tokenEndpoint(tokenEndpoint ->
                tokenEndpoint
                    .accessTokenRequestConverter(new PasswordGrantAuthenticationConverter())
                    .authenticationProvider(passwordGrantProvider)
            );

        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        // Disable CORS on auth server - gateway handles CORS for all downstream services
        http.cors(cors -> cors.disable());

        SecurityFilterChain chain = http.build();

        // Wire token generator and authorization service AFTER the chain is built
        // so the shared objects are fully initialized
        OAuth2TokenGenerator<?> tokenGenerator = http.getSharedObject(OAuth2TokenGenerator.class);
        OAuth2AuthorizationService authorizationService =
            http.getSharedObject(OAuth2AuthorizationService.class);
        passwordGrantProvider.setTokenGenerator(tokenGenerator);
        passwordGrantProvider.setAuthorizationService(authorizationService);

        return chain;
    }

    /**
     * Registered OAuth2 client.
     * Matches the clientId/clientSecret used by the React frontend.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("93ed453e-b7ac-4192-a6d4-c45fae0d99ac")
            .clientSecret(passwordEncoder.encode("client.devd123"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.PASSWORD)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("read")
            .scope("write")
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(client);
    }

    /**
     * JWK source using the existing RSA key from JWTKeystore.p12.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(keyStore.getInputStream(), keyStorePassword.toCharArray());

            RSAPublicKey publicKey = (RSAPublicKey) ks.getCertificate(keyPairAlias).getPublicKey();
            RSAPrivateKey privateKey = (RSAPrivateKey) ks.getKey(keyPairAlias, keyPairPassword.toCharArray());

            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyPairAlias)
                .build();

            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JWK from keystore", e);
        }
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Adds custom claims (user_id, authorities) to the JWT token.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (context.getPrincipal() != null && context.getPrincipal().getName() != null) {
                String username = context.getPrincipal().getName();
                Optional<User> userOpt = userRepository.findByUserName(username);
                userOpt.ifPresent(user -> {
                    context.getClaims().claim("user_id", user.getUserId());
                    // Add authorities claim so resource servers can do role-based access control
                    java.util.List<String> authorities = context.getPrincipal().getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toList());
                    context.getClaims().claim("authorities", authorities);
                });
            }
        };
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer(issuerUri)
            .build();
    }
}
