package com.company.fund.infrastructure.client;

import com.company.platform.web.exception.ApiException;
import com.company.platform.web.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * This is the first real consumer of a Phase 4 legacy-integration adapter
 * in this codebase — {@code integration/fund-mgmt-adapter} was built as a
 * template with no real consumer yet (docs/roadmap.md Phase 4 exit
 * criteria); this client is what that adapter is actually for.
 */
@Component
public class FundNavClient {

    private final RestClient fundNavRestClient;

    public FundNavClient(RestClient fundNavRestClient) {
        this.fundNavRestClient = fundNavRestClient;
    }

    public FundNavClientResponse getNav(String fundCode) {
        try {
            ApiResponse<FundNavClientResponse> response = fundNavRestClient.get()
                    .uri("/api/v1/funds/{fundCode}/nav", fundCode)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response.data();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FUND-5031",
                    "fund-mgmt-adapter is unavailable: " + e.getMessage());
        }
    }
}
