package com.company.investment.infrastructure.client;

import com.company.platform.web.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PortfolioPositionClient {

    private final RestClient portfolioServiceRestClient;

    public PortfolioPositionClient(RestClient portfolioServiceRestClient) {
        this.portfolioServiceRestClient = portfolioServiceRestClient;
    }

    /**
     * Materializes the subscription's position in Portfolio Service —
     * this is where "reserve units" actually becomes real (see
     * {@link com.company.investment.domain.Subscription}'s Javadoc).
     *
     * @throws ApiException if Portfolio Service rejects or is unreachable — the
     *                       caller (guide §8.4: "all saga steps... must be
     *                       idempotent") is expected to leave the subscription
     *                       AWAITING_PAYMENT on failure so a retry is safe.
     */
    public void recordPosition(UUID portfolioId, String fundCode, BigDecimal quantity) {
        try {
            portfolioServiceRestClient.post()
                    .uri("/api/v1/portfolios/{portfolioId}/positions", portfolioId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RecordPositionRequest(fundCode, quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INV-5034",
                    "portfolio-service is unavailable: " + e.getMessage());
        }
    }

    private record RecordPositionRequest(String fundCode, BigDecimal quantity) {
    }
}
