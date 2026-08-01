package com.company.platform.security.serviceauth;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;

class ServiceAuthRequestInterceptorTest {

    private static final int WIREMOCK_PORT = 9993;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void attachesTheAccessTokenAsABearerHeader() {
        stubFor(WireMock.post(urlPathEqualTo("/protocol/openid-connect/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"access_token": "stub-access-token", "token_type": "Bearer", "expires_in": 900}
                                """)));
        stubFor(get(urlPathEqualTo("/api/v1/downstream"))
                .willReturn(aResponse().withStatus(200)));

        var tokenProvider = new ServiceAuthTokenProvider("http://localhost:" + WIREMOCK_PORT, "api-platform-services",
                "local-dev-only-not-a-real-secret");
        var interceptor = new ServiceAuthRequestInterceptor(tokenProvider);
        var client = RestClient.builder()
                .baseUrl("http://localhost:" + WIREMOCK_PORT)
                .requestInterceptor(interceptor)
                .build();

        client.get().uri("/api/v1/downstream").retrieve().toBodilessEntity();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/downstream"))
                .withHeader("Authorization", matching("Bearer stub-access-token")));
    }

    @Test
    void addsNoAuthorizationHeaderWhenUnconfigured() {
        stubFor(get(urlPathEqualTo("/api/v1/downstream")).willReturn(aResponse().withStatus(200)));

        var tokenProvider = new ServiceAuthTokenProvider("", "api-platform-services", "");
        var interceptor = new ServiceAuthRequestInterceptor(tokenProvider);
        var client = RestClient.builder()
                .baseUrl("http://localhost:" + WIREMOCK_PORT)
                .requestInterceptor(interceptor)
                .build();

        client.get().uri("/api/v1/downstream").retrieve().toBodilessEntity();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/downstream"))
                .withoutHeader("Authorization"));
    }
}
