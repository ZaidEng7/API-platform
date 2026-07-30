package com.company.gateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates X-Correlation-Id when the caller doesn't supply one, and always
 * propagates it downstream (guide §7: Gateway responsibilities).
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;

        // Spring Security's firewall wraps the request's headers as
        // genuinely read-only (as of Spring Security 6.3.4+, patching
        // CVE-2024-38821) — request.mutate().header(...)/.headers(...)
        // both end up trying to mutate that read-only instance in place and
        // throw. A ServerHttpRequestDecorator sidesteps this entirely: it
        // builds a brand new HttpHeaders (copying, not mutating, the
        // original) rather than going through the mutate()/builder path.
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.set(CORRELATION_ID_HEADER, finalCorrelationId);
                return headers;
            }
        };

        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        return chain.filter(exchange.mutate().request(decoratedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
