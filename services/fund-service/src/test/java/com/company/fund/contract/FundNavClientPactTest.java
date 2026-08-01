package com.company.fund.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.fund.infrastructure.client.FundNavClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real consumer-side half of the Fund Service ↔ fund-mgmt-adapter pair
 * — Fund Service's own {@link FundNavClient}, the first real consumer of a
 * Phase 4 legacy-integration adapter in this codebase. Provider
 * verification lives in fund-mgmt-adapter's own
 * {@code FundMgmtAdapterPactVerificationTest}. Only one interaction: the
 * client's own catch block treats every non-2xx/network failure identically
 * (503), so there's nothing else about the response shape this consumer
 * actually depends on — see contracts testing philosophy in the platform
 * README history for why a not-found case isn't asserted here too.
 */
@ExtendWith(PactConsumerTestExt.class)
class FundNavClientPactTest {

    @Pact(consumer = "fund-service", provider = "fund-mgmt-adapter")
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

    @Test
    @PactTestFor(pactMethod = "getNav", pactVersion = PactSpecVersion.V3)
    void clientParsesNavResponse(MockServer mockServer) {
        var client = new FundNavClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        var nav = client.getNav("EQFND01");

        assertThat(nav.fundCode()).isEqualTo("EQFND01");
        assertThat(nav.navPerShare()).isEqualByComparingTo("12.3456");
    }
}
