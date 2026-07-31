package com.company.fundmgmtadapter.legacy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the §9.1 caching claim is real, not just an annotation: a second
 * call for the same fund never reaches the legacy system at all. This is
 * the one thing that actually distinguishes this adapter from
 * crm-adapter/onboarding-adapter — worth its own test, not folded into the
 * resilience test.
 */
@SpringBootTest
class FundNavProviderCachingTest {

    // Fixed, not dynamic — see the other two adapters' resilience tests for
    // why (@DynamicPropertySource runs before this extension's beforeAll()
    // binds a port).
    private static final int WIREMOCK_PORT = 9993;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyFundMgmtBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-fund-mgmt.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private FundNavProvider fundNavProvider;

    @Test
    void secondCallForTheSameFundIsServedFromCacheNotTheLegacySystem() {
        stubFor(get(urlPathMatching("/fundmgmt/v1/funds/.*/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"FUND_CD": "EQFND01", "NAV_VALUE_X10000": 105023, "NAV_DT_YYYYMMDD": "20260730"}
                                """)));

        var first = fundNavProvider.getNav("EQFND01");
        var second = fundNavProvider.getNav("EQFND01");

        assertThat(second).isEqualTo(first);
        wireMock.verify(1, getRequestedFor(urlPathMatching("/fundmgmt/v1/funds/.*/nav")));
    }

    @Test
    void differentFundsAreCachedIndependently() {
        stubFor(get(urlPathMatching("/fundmgmt/v1/funds/FUND-A/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"FUND_CD": "FUND-A", "NAV_VALUE_X10000": 100000, "NAV_DT_YYYYMMDD": "20260730"}
                                """)));
        stubFor(get(urlPathMatching("/fundmgmt/v1/funds/FUND-B/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"FUND_CD": "FUND-B", "NAV_VALUE_X10000": 200000, "NAV_DT_YYYYMMDD": "20260730"}
                                """)));

        fundNavProvider.getNav("FUND-A");
        fundNavProvider.getNav("FUND-B");

        wireMock.verify(1, getRequestedFor(urlPathMatching("/fundmgmt/v1/funds/FUND-A/nav")));
        wireMock.verify(1, getRequestedFor(urlPathMatching("/fundmgmt/v1/funds/FUND-B/nav")));
    }
}
