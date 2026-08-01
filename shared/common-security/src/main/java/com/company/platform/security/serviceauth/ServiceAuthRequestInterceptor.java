package com.company.platform.security.serviceauth;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Attaches a Bearer token to outbound internal service calls (ADR 0001).
 * Add via {@code RestClient.Builder#requestInterceptor(...)} to any
 * {@code RestClient} that calls another one of our own services — not to
 * clients calling a legacy-system adapter (out of scope per the ADR).
 */
public class ServiceAuthRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceAuthTokenProvider tokenProvider;

    public ServiceAuthRequestInterceptor(ServiceAuthTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        tokenProvider.getAccessToken().ifPresent(token -> request.getHeaders().setBearerAuth(token));
        return execution.execute(request, body);
    }
}
