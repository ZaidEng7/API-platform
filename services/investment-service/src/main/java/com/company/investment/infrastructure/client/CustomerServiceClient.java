package com.company.investment.infrastructure.client;

import com.company.platform.web.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class CustomerServiceClient {

    private final RestClient customerServiceRestClient;

    public CustomerServiceClient(RestClient customerServiceRestClient) {
        this.customerServiceRestClient = customerServiceRestClient;
    }

    /** @throws ApiException if the customer doesn't exist, or Customer Service is unreachable. */
    public void requireExists(UUID customerId) {
        try {
            customerServiceRestClient.get()
                    .uri("/api/v1/customers/{id}", customerId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INV-4044", "Customer not found: " + customerId);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INV-5031",
                    "customer-service is unavailable: " + e.getMessage());
        }
    }
}
