package com.company.platform.security.serviceauth;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round-trip against a stub token endpoint (guide's own "real
 * Testcontainers/WireMock, not mocks" discipline) — proves the no-op
 * behavior (ADR 0001: stays open until configured, matching
 * {@code CommonSecurityAutoConfiguration}'s own resource-server policy) and
 * that a token, once fetched, is cached rather than re-fetched on every call.
 */
class ServiceAuthTokenProviderTest {

    private static final int WIREMOCK_PORT = 9994;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @Test
    void isANoOpWhenIssuerUriIsBlank() {
        var provider = new ServiceAuthTokenProvider("", "api-platform-services", "some-secret");

        assertThat(provider.getAccessToken()).isEmpty();
    }

    @Test
    void isANoOpWhenClientSecretIsBlank() {
        var provider = new ServiceAuthTokenProvider("http://localhost:" + WIREMOCK_PORT, "api-platform-services", "");

        assertThat(provider.getAccessToken()).isEmpty();
    }

    @Test
    void fetchesAndCachesATokenWhenConfigured() {
        stubFor(WireMock.post(urlPathEqualTo("/protocol/openid-connect/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"access_token": "stub-access-token", "token_type": "Bearer", "expires_in": 900}
                                """)));

        var provider = new ServiceAuthTokenProvider("http://localhost:" + WIREMOCK_PORT, "api-platform-services",
                "local-dev-only-not-a-real-secret");

        assertThat(provider.getAccessToken()).contains("stub-access-token");
        assertThat(provider.getAccessToken()).contains("stub-access-token"); // second call — must reuse the cache

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/protocol/openid-connect/token")));
    }
}
