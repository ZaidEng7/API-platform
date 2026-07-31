package com.company.investment.infrastructure.client;

import com.company.platform.web.exception.ApiException;
import com.company.platform.web.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
public class AmlScreeningClient {

    private final RestClient amlServiceRestClient;

    public AmlScreeningClient(RestClient amlServiceRestClient) {
        this.amlServiceRestClient = amlServiceRestClient;
    }

    /**
     * @return {@code true} if the customer's most recent AML screening is
     *         COMPLETED with a CLEAR outcome, {@code false} if it's missing,
     *         still IN_PROGRESS, FAILED, or COMPLETED with a HIT.
     * @throws ApiException if AML Service is unreachable.
     */
    public boolean isClear(UUID customerId) {
        try {
            ApiResponse<List<AmlScreeningClientResponse>> response = amlServiceRestClient.get()
                    .uri("/api/v1/aml/screenings?customerId={customerId}&page=0&size=1", customerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<AmlScreeningClientResponse> screenings = response.data();
            if (screenings.isEmpty()) {
                return false;
            }
            var latest = screenings.get(0);
            return "COMPLETED".equals(latest.status()) && "CLEAR".equals(latest.outcome());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INV-5033",
                    "aml-service is unavailable: " + e.getMessage());
        }
    }
}
