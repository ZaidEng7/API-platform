package com.company.portfolio.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.platform.web.exception.ApiException;
import com.company.portfolio.infrastructure.client.FundNavClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real consumer-side half of the Portfolio Service ↔ Fund Service pair —
 * Portfolio Service's own {@link FundNavClient}, the second hop in the
 * Portfolio → Fund Service → fund-mgmt-adapter → legacy chain. Provider
 * verification lives in Fund Service's own
 * {@code FundServicePactVerificationTest}. Two interactions, unlike the
 * fund-service-to-adapter pair: this client's catch block distinguishes a
 * 404 (fund exists but has no NAV yet) from every other failure.
 */
@ExtendWith(PactConsumerTestExt.class)
class FundNavClientPactTest {

    @Pact(consumer = "portfolio-service", provider = "fund-service")
    RequestResponsePact getNav(PactDslWithProvider builder) {
        return builder
                .given("fund EQFND01 has a current NAV")
                .uponReceiving("a request for that fund's NAV")
                .path("/api/v1/funds/EQFND01/nav")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body("""
                        {
                          "success": true,
                          "data": {
                            "fundCode": "EQFND01",
                            "navPerShare": 12.3456,
                            "asOfDate": "2026-08-01"
                          }
                        }
                        """)
                .toPact();
    }

    @Pact(consumer = "portfolio-service", provider = "fund-service")
    RequestResponsePact getNavForFundWithNoNav(PactDslWithProvider builder) {
        return builder
                .given("fund NONAV01 exists but has no NAV yet")
                .uponReceiving("a request for that fund's NAV")
                .path("/api/v1/funds/NONAV01/nav")
                .method("GET")
                .willRespondWith()
                .status(404)
                .headers(Map.of("Content-Type", "application/problem+json"))
                .body("""
                        {
                          "status": 404,
                          "errorCode": "FUND-4042"
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getNav", pactVersion = PactSpecVersion.V3)
    void clientParsesNavResponse(MockServer mockServer) {
        var client = new FundNavClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        var nav = client.getNav("EQFND01");

        assertThat(nav.fundCode()).isEqualTo("EQFND01");
        assertThat(nav.navPerShare()).isEqualByComparingTo("12.3456");
    }

    @Test
    @PactTestFor(pactMethod = "getNavForFundWithNoNav", pactVersion = PactSpecVersion.V3)
    void clientThrowsWhenNoNavAvailable(MockServer mockServer) {
        var client = new FundNavClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThatThrownBy(() -> client.getNav("NONAV01"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("NONAV01");
    }
}
