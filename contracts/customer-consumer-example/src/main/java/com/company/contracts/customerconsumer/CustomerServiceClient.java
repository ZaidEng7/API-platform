package com.company.contracts.customerconsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * TEMPLATE — a minimal HTTP client standing in for a real consumer of
 * customer-service's GET /api/v1/customers/{id}. It exists only so the Pact
 * consumer test in this module has something concrete to exercise against
 * the Pact mock server. See ../../../README.md before modeling a real
 * consumer on this.
 */
public class CustomerServiceClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String baseUrl;

    public CustomerServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public CustomerDto getById(UUID id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/customers/" + id))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new CustomerNotFoundException(id);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unexpected status " + response.statusCode() + " from customer-service");
        }

        ApiEnvelope envelope = objectMapper.readValue(response.body(), ApiEnvelope.class);
        return envelope.data();
    }

    public record CustomerDto(UUID id, String fullName, String email, Instant createdAt) {
    }

    record ApiEnvelope(boolean success, CustomerDto data) {
    }

    public static class CustomerNotFoundException extends RuntimeException {
        public CustomerNotFoundException(UUID id) {
            super("Customer not found: " + id);
        }
    }
}
