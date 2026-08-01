package com.company.platform.security.autoconfigure;

import com.company.platform.security.jwt.KeycloakRealmRoleConverter;
import com.company.platform.security.serviceauth.ServiceAuthRequestInterceptor;
import com.company.platform.security.serviceauth.ServiceAuthTokenProvider;
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

    /**
     * Always registered, like {@link #apiSecurityFilterChain}: a no-op
     * (never fetches a token) unless {@code issuer-uri} and the service
     * client secret are both configured (ADR 0001) — reuses this service's
     * own resource-server {@code issuer-uri}, since a service already
     * trusts that realm for incoming requests and can fetch its own
     * outbound token from the same one.
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceAuthTokenProvider serviceAuthTokenProvider(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${platform.security.service-auth.client-id:api-platform-services}") String clientId,
            @Value("${platform.security.service-auth.client-secret:}") String clientSecret) {
        return new ServiceAuthTokenProvider(issuerUri, clientId, clientSecret);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceAuthRequestInterceptor serviceAuthRequestInterceptor(ServiceAuthTokenProvider serviceAuthTokenProvider) {
        return new ServiceAuthRequestInterceptor(serviceAuthTokenProvider);
    }
}
