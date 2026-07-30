package com.company.platform.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Convenience access to the calling principal's identity, for use in
 * object-level authorization checks (guide §12.2: every {@code GET
 * /portfolios/{id}} must verify ownership — the #1 API vulnerability class,
 * BOLA/IDOR). The ownership check itself is domain-specific and stays in
 * each service's application layer; this just answers "who is calling".
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<String> subject() {
        return jwt().map(Jwt::getSubject);
    }

    public static boolean hasRole(String role) {
        String authority = "ROLE_" + role.toUpperCase();
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equalsIgnoreCase(authority));
    }

    private static Optional<Jwt> jwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }
}
