package com.company.gateway.canary;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable, in-memory canary weight per migration ("route" in the guide's
 * §26 sense of a consumer relationship being migrated, not a Spring Cloud
 * Gateway route). Deliberately not Spring Cloud Gateway's own static
 * {@code Weight} filter: that value is fixed at startup from YAML, so
 * changing it means a redeploy — this registry lets {@link
 * com.company.gateway.canary.CanaryAdminController} flip the weight at
 * runtime, which is what the guide's own "instant rollback = flag off"
 * (§25 Phase 6) actually requires. A real feature-flag service
 * (LaunchDarkly, GrowthBook) would replace this with something backed by
 * persistent, audited config — not built here, since none exists in this
 * platform yet and this is a demonstration of the mechanism, not a
 * production flagging system.
 */
@Component
public class CanaryWeightRegistry {

    private final Map<String, AtomicInteger> legacyWeightPercentByMigration = new ConcurrentHashMap<>();

    /** @return the current legacy-shadow percentage (0-100) for a migration, 0 if never set. */
    public int getLegacyWeightPercent(String migrationId) {
        return legacyWeightPercentByMigration.getOrDefault(migrationId, new AtomicInteger(0)).get();
    }

    /** @throws IllegalArgumentException if percent isn't between 0 and 100 inclusive. */
    public void setLegacyWeightPercent(String migrationId, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("legacyWeightPercent must be between 0 and 100, was: " + percent);
        }
        legacyWeightPercentByMigration.computeIfAbsent(migrationId, key -> new AtomicInteger()).set(percent);
    }
}
