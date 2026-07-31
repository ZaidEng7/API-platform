package com.company.investment.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Four RestClient beans, one per downstream service this saga calls — all
 * of our own services, not legacy systems, so (same rationale as Fund
 * Service's and Portfolio Service's own client configs) connect/read
 * timeouts only, not the full §9.4 resilience table.
 *
 * <p><b>Known gap this surfaces:</b> none of these calls carry any
 * service-to-service credential. Guide §8.1 says service-to-service calls
 * should go through mTLS, which is a mesh/infrastructure concern this repo
 * doesn't provision — but in the meantime, KYC/AML/Portfolio Service's own
 * {@code @PreAuthorize} method-security gates deny *any* caller without a
 * role-bearing authentication, including a plain anonymous service call
 * with no Authorization header at all (Spring Security's
 * AnonymousAuthenticationFilter still populates a role-less principal even
 * when the surrounding filter chain permits the request through). So
 * today, these calls would be rejected with 403 the moment any of those
 * services' {@code issuer-uri} is actually configured — this saga hasn't
 * been run against a real secured deployment, only against WireMock stubs
 * in tests (which don't enforce security at all). See this module's
 * README.
 */
@Configuration
public class ExternalServiceClientConfig {

    @Bean
    public RestClient customerServiceRestClient(@Value("${customer-service.base-url}") String baseUrl) {
        return restClient(baseUrl);
    }

    @Bean
    public RestClient kycServiceRestClient(@Value("${kyc-service.base-url}") String baseUrl) {
        return restClient(baseUrl);
    }

    @Bean
    public RestClient amlServiceRestClient(@Value("${aml-service.base-url}") String baseUrl) {
        return restClient(baseUrl);
    }

    @Bean
    public RestClient portfolioServiceRestClient(@Value("${portfolio-service.base-url}") String baseUrl) {
        return restClient(baseUrl);
    }

    private RestClient restClient(String baseUrl) {
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
