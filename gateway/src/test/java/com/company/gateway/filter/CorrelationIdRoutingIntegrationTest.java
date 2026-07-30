package com.company.gateway.filter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real routing + filter chain, against a stub backend (a bare JDK
 * HttpServer — no Testcontainers needed here, unlike DB/broker-backed
 * services). Verifies the actual guarantee CorrelationIdFilter makes:
 * generate-if-absent, propagate-if-present, both to the downstream request
 * and the response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdRoutingIntegrationTest {

    private static final AtomicReference<String> RECEIVED_CORRELATION_ID = new AtomicReference<>();
    private static final HttpServer STUB_BACKEND;

    static {
        try {
            STUB_BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        STUB_BACKEND.createContext("/api/v1/customers/echo", exchange -> {
            RECEIVED_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        STUB_BACKEND.start();
    }

    @Autowired
    private WebTestClient webTestClient;

    @AfterAll
    static void stopStubBackend() {
        STUB_BACKEND.stop(0);
    }

    @DynamicPropertySource
    static void routeToStubBackend(DynamicPropertyRegistry registry) {
        int port = STUB_BACKEND.getAddress().getPort();
        registry.add("spring.cloud.gateway.routes[0].id", () -> "customer-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + port);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/customers/**");
    }

    @Test
    void generatesCorrelationIdWhenAbsent() {
        webTestClient.get()
                .uri("/api/v1/customers/echo")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");

        assertThat(RECEIVED_CORRELATION_ID.get()).isNotBlank();
    }

    @Test
    void propagatesIncomingCorrelationId() {
        webTestClient.get()
                .uri("/api/v1/customers/echo")
                .header("X-Correlation-Id", "test-corr-id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "test-corr-id");

        assertThat(RECEIVED_CORRELATION_ID.get()).isEqualTo("test-corr-id");
    }
}
