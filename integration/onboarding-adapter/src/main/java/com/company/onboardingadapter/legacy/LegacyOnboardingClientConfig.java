package com.company.onboardingadapter.legacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Connect/read timeouts are the two §9.4 controls that apply per-request
 * rather than per-retry-attempt, so they're configured on the HTTP client
 * itself instead of via a resilience4j annotation.
 */
@Configuration
public class LegacyOnboardingClientConfig {

    @Bean
    public RestClient legacyOnboardingRestClient(@Value("${legacy-onboarding.base-url}") String baseUrl) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(10)); // known-slow legacy op, §9.4's 10s max
        // httpComponents(), not detect() (which prefers the JDK client's
        // HTTP/2-over-cleartext attempt) — a real legacy system is
        // essentially guaranteed to be HTTP/1.1 only, and h2c against a
        // server that doesn't expect it produces RST_STREAM failures on
        // POST-with-body (confirmed against WireMock's embedded Jetty).
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.httpComponents().build(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
