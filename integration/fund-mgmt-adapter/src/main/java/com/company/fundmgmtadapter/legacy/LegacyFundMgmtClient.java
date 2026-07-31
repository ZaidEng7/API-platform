package com.company.fundmgmtadapter.legacy;

import com.company.fundmgmtadapter.legacy.dto.LegacyFundNavRecord;
import com.company.platform.web.exception.ApiException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TEMPLATE — the anti-corruption boundary's resilient-fetch half (guide
 * §9.1). Deliberately has no caching itself: {@link FundNavProvider} (a
 * separate bean, not just another method here) is what applies
 * {@code @Cacheable}, so a cache hit never even reaches this class — Spring
 * AOP proxies don't intercept same-class method calls, so splitting
 * caching and resilience across two beans isn't just tidiness, it's
 * required for the cache to actually short-circuit the network call.
 */
@Component
public class LegacyFundMgmtClient {

    private final RestClient legacyFundMgmtRestClient;

    public LegacyFundMgmtClient(RestClient legacyFundMgmtRestClient) {
        this.legacyFundMgmtRestClient = legacyFundMgmtRestClient;
    }

    @CircuitBreaker(name = "legacyFundMgmt", fallbackMethod = "fallback")
    @Retry(name = "legacyFundMgmt", fallbackMethod = "fallback")
    @Bulkhead(name = "legacyFundMgmt", fallbackMethod = "fallback")
    public LegacyFundNavRecord getNav(String fundCode) {
        return legacyFundMgmtRestClient.get()
                .uri("/fundmgmt/v1/funds/{fundCode}/nav", fundCode)
                .retrieve()
                .body(LegacyFundNavRecord.class);
    }

    @SuppressWarnings("unused") // invoked reflectively by resilience4j
    private LegacyFundNavRecord fallback(String fundCode, Throwable cause) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FND-5031",
                "Legacy fund management system is unavailable: " + cause.getMessage());
    }
}
