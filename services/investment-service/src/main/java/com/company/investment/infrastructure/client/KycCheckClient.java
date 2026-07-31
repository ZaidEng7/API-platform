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
public class KycCheckClient {

    private final RestClient kycServiceRestClient;

    public KycCheckClient(RestClient kycServiceRestClient) {
        this.kycServiceRestClient = kycServiceRestClient;
    }

    /**
     * @return {@code true} if the customer's most recent KYC check is APPROVED,
     *         {@code false} if it's missing, PENDING, or REJECTED.
     * @throws ApiException if KYC Service is unreachable.
     */
    public boolean isApproved(UUID customerId) {
        try {
            ApiResponse<List<KycCheckClientResponse>> response = kycServiceRestClient.get()
                    .uri("/api/v1/kyc-checks?customerId={customerId}&page=0&size=1", customerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<KycCheckClientResponse> checks = response.data();
            return !checks.isEmpty() && "APPROVED".equals(checks.get(0).status());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INV-5032",
                    "kyc-service is unavailable: " + e.getMessage());
        }
    }
}
