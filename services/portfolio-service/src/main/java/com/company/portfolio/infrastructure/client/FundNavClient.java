package com.company.portfolio.infrastructure.client;

import com.company.platform.web.exception.ApiException;
import com.company.platform.web.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * This is the second real inter-service consumer in this codebase (after
 * Fund Service → fund-mgmt-adapter) — the same chain extended one hop
 * further: Portfolio → Fund Service → fund-mgmt-adapter → legacy Fund
 * Management product.
 */
@Component
public class FundNavClient {

    private final RestClient fundServiceRestClient;

    public FundNavClient(RestClient fundServiceRestClient) {
        this.fundServiceRestClient = fundServiceRestClient;
    }

    public FundNavClientResponse getNav(String fundCode) {
        try {
            ApiResponse<FundNavClientResponse> response = fundServiceRestClient.get()
                    .uri("/api/v1/funds/{fundCode}/nav", fundCode)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO-4043", "NAV not available for fund: " + fundCode);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PORTFOLIO-5031",
                    "fund-service is unavailable: " + e.getMessage());
        }
    }
}
