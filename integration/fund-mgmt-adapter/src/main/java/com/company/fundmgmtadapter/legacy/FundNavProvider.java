package com.company.fundmgmtadapter.legacy;

import com.company.fundmgmtadapter.api.dto.FundNavResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import static com.company.fundmgmtadapter.legacy.FundNavCacheConfig.FUND_NAV_CACHE;

/**
 * The caching half of the anti-corruption layer (guide §9.1) — see
 * FundNavCacheConfig for why a TTL-based cache is the correct design here,
 * not just an optimization. Deliberately a separate bean from {@link
 * LegacyFundMgmtClient}: Spring's proxy-based {@code @Cacheable} and
 * resilience4j's proxy-based annotations both need to go through the
 * Spring-managed proxy to take effect, and a same-class call
 * ({@code this.someOtherMethod()}) bypasses the proxy entirely.
 */
@Component
public class FundNavProvider {

    private final LegacyFundMgmtClient legacyFundMgmtClient;
    private final LegacyFundMgmtTranslator translator;

    public FundNavProvider(LegacyFundMgmtClient legacyFundMgmtClient, LegacyFundMgmtTranslator translator) {
        this.legacyFundMgmtClient = legacyFundMgmtClient;
        this.translator = translator;
    }

    @Cacheable(FUND_NAV_CACHE)
    public FundNavResponse getNav(String fundCode) {
        return translator.toCanonical(legacyFundMgmtClient.getNav(fundCode));
    }
}
