package com.company.platform.security.serviceauth;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;

import java.util.Optional;

/**
 * Fetches and caches an OAuth2 Client Credentials access token for
 * service-to-service calls (ADR 0001,
 * {@code docs/adr/0001-service-to-service-authentication.md}), against the
 * {@code api-platform-services} confidential client
 * {@code platform/identity/realm-export.json} already provisions for this.
 *
 * <p>Token caching/refresh is handled by
 * {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} itself — not
 * hand-rolled here — which re-fetches only once the cached token is no
 * longer valid.
 *
 * <p>Deliberately a no-op (never fetches anything, {@link #getAccessToken()}
 * always returns empty) when either {@code issuer-uri} or the client secret
 * isn't configured — the same "stays fully open until configured" policy
 * {@code CommonSecurityAutoConfiguration}'s own resource-server wiring
 * already uses, so every existing test and dev-mode deployment keeps
 * behaving exactly as before this class existed.
 */
public class ServiceAuthTokenProvider {

    private static final String REGISTRATION_ID = "service-auth";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public ServiceAuthTokenProvider(String issuerUri, String clientId, String clientSecret) {
        if (issuerUri == null || issuerUri.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            this.authorizedClientManager = null;
            this.registrationId = null;
            return;
        }

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .tokenUri(issuerUri + "/protocol/openid-connect/token")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        InMemoryClientRegistrationRepository clientRegistrationRepository =
                new InMemoryClientRegistrationRepository(clientRegistration);
        OAuth2AuthorizedClientService authorizedClientService =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository,
                        authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());

        this.authorizedClientManager = manager;
        this.registrationId = REGISTRATION_ID;
    }

    /** @return the current access token, or empty if service-to-service auth isn't configured. */
    public Optional<String> getAccessToken() {
        if (authorizedClientManager == null) {
            return Optional.empty();
        }
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(REGISTRATION_ID)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(request);
        return Optional.ofNullable(authorizedClient).map(client -> client.getAccessToken().getTokenValue());
    }
}
