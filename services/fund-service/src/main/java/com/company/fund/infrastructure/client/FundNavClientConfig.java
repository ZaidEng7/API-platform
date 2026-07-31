package com.company.fund.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * fund-mgmt-adapter is one of our own services, not a legacy system, so
 * the full §9.4 resilience table (retry/circuit-breaker/bulkhead) doesn't
 * apply the way it does for a legacy-facing client — but connect/read
 * timeouts are still cheap, uncontroversial protection for any network
 * call. {@code .httpComponents()} (not {@code .detect()}) is kept for
 * consistency with the adapters' own established client config, even
 * though the callee here is a normal Spring Boot service rather than a
 * legacy system that can't speak HTTP/2.
 */
@Configuration
public class FundNavClientConfig {

    @Bean
    public RestClient fundNavRestClient(@Value("${fund-mgmt-adapter.base-url}") String baseUrl) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.httpComponents().build(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
