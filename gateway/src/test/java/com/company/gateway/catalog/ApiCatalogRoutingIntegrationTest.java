package com.company.gateway.catalog;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Proves the API catalog (guide §7.28, Phase 7) actually proxies a business
 * service's own {@code /v3/api-docs} through the Gateway's {@code
 * /api-docs/<service>/**} route with the correct {@code RewritePath}, and
 * that the Gateway serves its own aggregated Swagger UI + spec (now that
 * springdoc-openapi-starter-webflux-ui is on the classpath). {@code
 * CUSTOMER_SERVICE_URI} points both the business route and its
 * {@code customer-service-docs} counterpart at the same stub — same
 * dynamic-property-override approach {@code CorrelationIdRoutingIntegrationTest}
 * and {@code CustomerLookupCanaryControllerTest} already use, so no
 * per-route-index wiring is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiCatalogRoutingIntegrationTest {

    private static final HttpServer CUSTOMER_SERVICE_STUB;

    static {
        try {
            CUSTOMER_SERVICE_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CUSTOMER_SERVICE_STUB.createContext("/v3/api-docs", exchange -> {
            byte[] body = "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"customer-service-stub\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        CUSTOMER_SERVICE_STUB.start();
    }

    @Autowired
    private WebTestClient webTestClient;

    @AfterAll
    static void stopStub() {
        CUSTOMER_SERVICE_STUB.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("CUSTOMER_SERVICE_URI",
                () -> "http://localhost:" + CUSTOMER_SERVICE_STUB.getAddress().getPort());
    }

    @Test
    void proxiesAndRewritesCustomerServiceDocsRoute() {
        webTestClient.get()
                .uri("/api-docs/customer-service/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("customer-service-stub"));
    }

    @Test
    void servesItsOwnAggregatedApiDocs() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("\"openapi\""));
    }

    @Test
    void swaggerUiEndpointIsWired() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }
}
