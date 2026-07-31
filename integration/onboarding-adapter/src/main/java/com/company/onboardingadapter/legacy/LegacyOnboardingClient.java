package com.company.onboardingadapter.legacy;

import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRecord;
import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRequest;
import com.company.platform.web.exception.ApiException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TEMPLATE — the anti-corruption boundary for a legacy <em>write</em>
 * (guide §9.1). Deliberately has no {@code @Retry}: this is a create
 * operation, and per §9.4 "never blind-retry" a non-idempotent write —
 * without knowing whether the legacy call actually failed before or after
 * it took effect, a retry risks a duplicate application. Circuit breaker
 * and bulkhead still apply (they protect the caller, not the legacy
 * system's data), and the fallback still degrades honestly to a 503
 * instead of hanging.
 *
 * <p>This also means the fictional legacy system here can't participate in
 * a saga (no compensation endpoint exists) — §9.3 requires stating that
 * plainly and designing any real flow using it as confirm-then-execute (do
 * this irreversible write last). See README.md.
 */
@Component
public class LegacyOnboardingClient {

    private final RestClient legacyOnboardingRestClient;

    public LegacyOnboardingClient(RestClient legacyOnboardingRestClient) {
        this.legacyOnboardingRestClient = legacyOnboardingRestClient;
    }

    @CircuitBreaker(name = "legacyOnboarding", fallbackMethod = "fallback")
    @Bulkhead(name = "legacyOnboarding", fallbackMethod = "fallback")
    public LegacyOnboardingApplicationRecord submit(LegacyOnboardingApplicationRequest request) {
        return legacyOnboardingRestClient.post()
                .uri("/onboarding/v1/applications")
                .body(request)
                .retrieve()
                .body(LegacyOnboardingApplicationRecord.class);
    }

    @SuppressWarnings("unused") // invoked reflectively by resilience4j
    private LegacyOnboardingApplicationRecord fallback(LegacyOnboardingApplicationRequest request, Throwable cause) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ONB-5031",
                "Legacy onboarding system is unavailable: " + cause.getMessage());
    }
}
