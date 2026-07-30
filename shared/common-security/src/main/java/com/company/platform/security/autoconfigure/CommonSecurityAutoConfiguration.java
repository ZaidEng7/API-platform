package com.company.platform.security.autoconfigure;

import com.company.platform.security.jwt.KeycloakRealmRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource-server wiring for services that need it. This bean is always
 * registered (never behind {@code @ConditionalOnProperty}) and branches
 * internally on whether {@code issuer-uri} is set: merely having
 * {@code spring-boot-starter-security} on the classpath makes Spring Boot
 * auto-configure a default {@code SecurityFilterChain} that requires auth
 * everywhere with a generated password, and the only way to prevent that
 * default from silently taking over is to always define our own chain —
 * even when it has nothing to enforce yet (i.e. before Identity/Keycloak is
 * available — see docs/roadmap.md Phase 3). {@code @PreAuthorize} method
 * security still works as expected either way, since it evaluates the
 * current Authentication (anonymous when the chain is open) independently
 * of the filter chain's own permitAll/authenticated decision.
 *
 * <p>Default policy once enforced: actuator health/info and OpenAPI docs
 * are public, everything else requires a valid JWT. Coarse-grained only
 * (guide §7, §12.2) — fine-grained/object-level authorization stays in
 * each service.
 */
@AutoConfiguration
@EnableMethodSecurity
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        if (issuerUri == null || issuerUri.isBlank()) {
            return http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
