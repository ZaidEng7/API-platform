package com.company.portfolio.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fund Service is one of our own services, not a legacy system, so the
 * full §9.4 resilience table doesn't apply here — see Fund Service's own
 * {@code FundNavClientConfig} for the identical rationale one hop further
 * down this same call chain (Portfolio → Fund Service → fund-mgmt-adapter
 * → legacy Fund Management product).
 */
@Configuration
public class FundNavClientConfig {

    @Bean
    public RestClient fundServiceRestClient(@Value("${fund-service.base-url}") String baseUrl) {
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
