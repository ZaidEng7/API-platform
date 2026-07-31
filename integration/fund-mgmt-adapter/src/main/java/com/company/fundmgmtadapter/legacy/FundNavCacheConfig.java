package com.company.fundmgmtadapter.legacy;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Guide §9.1 lists caching as an adapter responsibility alongside
 * translation and resilience — this is the one piece neither crm-adapter
 * nor onboarding-adapter needed. It's not a performance shortcut: this
 * fictional legacy system only republishes NAV once a day after market
 * close, so a TTL shorter than that refresh window would just be extra
 * unnecessary load on it, and a TTL longer would serve genuinely stale
 * data. See README.md for the (fictional) constraint this is modeling.
 */
@Configuration
public class FundNavCacheConfig {

    public static final String FUND_NAV_CACHE = "fundNav";

    @Bean
    public CacheManager cacheManager(@Value("${fund-mgmt.nav-cache-ttl:PT30M}") Duration navCacheTtl) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(FUND_NAV_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(navCacheTtl));
        return cacheManager;
    }
}
