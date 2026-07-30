package com.company.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts Keycloak realm roles ({@code realm_access.roles}) into Spring
 * Security authorities (guide §12.2: RBAC roles are configurable data).
 * Duplicated from {@code common-security}'s identically-named class rather
 * than shared: that module's autoconfiguration wires a servlet
 * {@code SecurityFilterChain}, which isn't resolvable on the Gateway's
 * WebFlux-only classpath — this class itself is tiny and stack-agnostic,
 * so duplicating it is cheaper and safer than coupling the two stacks.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .map(String.class::cast)
                .map(role -> "ROLE_" + role.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'))
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
