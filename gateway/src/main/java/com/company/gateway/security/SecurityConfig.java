package com.company.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT AuthN offload at the Gateway (guide §7, §12.1) — coarse-grained only,
 * fine-grained authorization stays in each service.
 *
 * <p>This bean is registered unconditionally, and branches internally on
 * whether {@code issuer-uri} is configured (i.e. whether Identity/Keycloak
 * is actually running — see platform/identity/). That's deliberate: adding
 * {@code spring-boot-starter-security} to the classpath makes Spring Boot
 * auto-configure a default {@code SecurityWebFilterChain} that requires
 * auth everywhere with a generated password — the only way to prevent that
 * default from taking over is to always define our own chain, even when it
 * has nothing to enforce yet.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            CorsConfigurationSource corsConfigurationSource) {
        if (issuerUri == null || issuerUri.isBlank()) {
            return http
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .build();
        }

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/gateway/**", "/actuator/prometheus")
                        .permitAll()
                        // CORS preflight carries no Authorization header by design
                        // (browser-issued, not app code) — this only covers routes
                        // Spring Cloud Gateway's own proxying doesn't already
                        // handle preflight for, i.e. plain @RestControllers like
                        // CustomerLookupCanaryController/CanaryAdminController,
                        // which spring.cloud.gateway.globalcors never reaches.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Runtime canary-weight control (guide §25 Phase 6) — an
                        // operational lever, not a business-service endpoint, so
                        // gated to administrator here rather than left to each
                        // downstream service's own @PreAuthorize.
                        .pathMatchers("/admin/canary/**").hasRole("ADMINISTRATOR")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    /**
     * Mirrors {@code spring.cloud.gateway.globalcors} (same env var, same
     * defaults) so both request paths — Gateway-proxied routes and plain
     * {@code @RestController}s in this app — get identical CORS behavior.
     * {@code X-Canary-Target} must be explicitly exposed: browsers hide all
     * non-simple response headers from cross-origin JS callers unless the
     * server lists them in {@code Access-Control-Expose-Headers}, even
     * though the header is present on the wire either way.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${GATEWAY_CORS_ALLOWED_ORIGINS:http://localhost:4200,http://localhost:4201}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Canary-Target"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
