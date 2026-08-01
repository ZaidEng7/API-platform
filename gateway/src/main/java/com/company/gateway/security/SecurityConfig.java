package com.company.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

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
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            return http
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .build();
        }

        return http
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/gateway/**", "/actuator/prometheus")
                        .permitAll()
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

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
