package com.company.investment.infrastructure.client;

import com.company.platform.security.serviceauth.ServiceAuthRequestInterceptor;
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
 * <p>Each carries a {@link ServiceAuthRequestInterceptor}, attaching an
 * OAuth2 Client Credentials Bearer token (ADR 0001,
 * {@code docs/adr/0001-service-to-service-authentication.md}) — closes the
 * gap this class's own Javadoc used to describe as unresolved: KYC/AML/
 * Portfolio/Customer Service's {@code @PreAuthorize} gates deny any caller
 * without a role-bearing authentication, so an unauthenticated call would
 * be rejected with 403 the moment any of those services' {@code issuer-uri}
 * is configured for real. The interceptor is a no-op until this service's
 * own {@code platform.security.service-auth.client-secret} is configured,
 * so dev/test behavior is unchanged until then.
 */
@Configuration
public class ExternalServiceClientConfig {

    @Bean
    public RestClient customerServiceRestClient(@Value("${customer-service.base-url}") String baseUrl,
                                                 ServiceAuthRequestInterceptor serviceAuthRequestInterceptor) {
        return restClient(baseUrl, serviceAuthRequestInterceptor);
    }

    @Bean
    public RestClient kycServiceRestClient(@Value("${kyc-service.base-url}") String baseUrl,
                                            ServiceAuthRequestInterceptor serviceAuthRequestInterceptor) {
        return restClient(baseUrl, serviceAuthRequestInterceptor);
    }

    @Bean
    public RestClient amlServiceRestClient(@Value("${aml-service.base-url}") String baseUrl,
                                            ServiceAuthRequestInterceptor serviceAuthRequestInterceptor) {
        return restClient(baseUrl, serviceAuthRequestInterceptor);
    }

    @Bean
    public RestClient portfolioServiceRestClient(@Value("${portfolio-service.base-url}") String baseUrl,
                                                  ServiceAuthRequestInterceptor serviceAuthRequestInterceptor) {
        return restClient(baseUrl, serviceAuthRequestInterceptor);
    }

    private RestClient restClient(String baseUrl, ServiceAuthRequestInterceptor serviceAuthRequestInterceptor) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.httpComponents().build(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(serviceAuthRequestInterceptor)
                .build();
    }
}
