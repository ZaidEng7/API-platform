package com.company.gateway.canary;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round-trip against two stub backends (a bare JDK
 * {@code HttpServer} each, same choice {@code CorrelationIdRoutingIntegrationTest}
 * already made) standing in for Customer Service (the migrated target)
 * and {@code crm-adapter} (the legacy shadow path) — proves the weighted
 * split and the {@code X-Canary-Target} header actually work, and that
 * {@link CanaryAdminController} changes take effect on the very next
 * request with no restart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CustomerLookupCanaryControllerTest {

    private static final HttpServer CUSTOMER_SERVICE_STUB;
    private static final HttpServer CRM_ADAPTER_STUB;

    static {
        try {
            CUSTOMER_SERVICE_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            CRM_ADAPTER_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CUSTOMER_SERVICE_STUB.createContext("/api/v1/customers/1", exchange -> respond(exchange, "customer-service-response"));
        CRM_ADAPTER_STUB.createContext("/api/v1/crm-customers/1", exchange -> respond(exchange, "crm-adapter-response"));
        CUSTOMER_SERVICE_STUB.start();
        CRM_ADAPTER_STUB.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CanaryWeightRegistry weightRegistry;

    @AfterAll
    static void stopStubs() {
        CUSTOMER_SERVICE_STUB.stop(0);
        CRM_ADAPTER_STUB.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStubs(DynamicPropertyRegistry registry) {
        registry.add("CUSTOMER_SERVICE_URI", () -> "http://localhost:" + CUSTOMER_SERVICE_STUB.getAddress().getPort());
        registry.add("CRM_ADAPTER_URI", () -> "http://localhost:" + CRM_ADAPTER_STUB.getAddress().getPort());
    }

    @BeforeEach
    void resetWeight() {
        weightRegistry.setLegacyWeightPercent(CustomerLookupCanaryController.MIGRATION_ID, 0);
    }

    @Test
    void servesFromCustomerServiceAtZeroPercentLegacyWeight() {
        webTestClient.get().uri("/api/v1/customer-lookup/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Canary-Target", "customer-service")
                .expectBody(String.class).isEqualTo("customer-service-response");
    }

    @Test
    void servesFromCrmAdapterAtHundredPercentLegacyWeight() {
        weightRegistry.setLegacyWeightPercent(CustomerLookupCanaryController.MIGRATION_ID, 100);

        webTestClient.get().uri("/api/v1/customer-lookup/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Canary-Target", "crm-adapter")
                .expectBody(String.class).isEqualTo("crm-adapter-response");
    }

    @Test
    void adminEndpointChangesTheWeightWithNoRestart() {
        webTestClient.get().uri("/api/v1/customer-lookup/1")
                .exchange()
                .expectHeader().valueEquals("X-Canary-Target", "customer-service");

        webTestClient.post().uri("/admin/canary/customer-lookup?legacyWeightPercent=100")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/customer-lookup/1")
                .exchange()
                .expectHeader().valueEquals("X-Canary-Target", "crm-adapter");

        assertThat(weightRegistry.getLegacyWeightPercent(CustomerLookupCanaryController.MIGRATION_ID)).isEqualTo(100);
    }
}
