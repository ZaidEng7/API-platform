package com.company.gateway.canary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Runtime control of the canary split — gated to {@code administrator} at
 * the path level in {@code SecurityConfig} (the Gateway's own established
 * "coarse-grained only" auth philosophy), not per-method
 * {@code @PreAuthorize}. "Instant rollback = flag off" (guide §25 Phase 6)
 * means {@code POST .../customer-lookup?legacyWeightPercent=0} — no
 * redeploy.
 */
@RestController
@RequestMapping("/admin/canary")
public class CanaryAdminController {

    private final CanaryWeightRegistry weightRegistry;

    public CanaryAdminController(CanaryWeightRegistry weightRegistry) {
        this.weightRegistry = weightRegistry;
    }

    @GetMapping("/customer-lookup")
    public Map<String, Integer> getWeight() {
        return Map.of("legacyWeightPercent",
                weightRegistry.getLegacyWeightPercent(CustomerLookupCanaryController.MIGRATION_ID));
    }

    @PostMapping("/customer-lookup")
    public Map<String, Integer> setWeight(@RequestParam int legacyWeightPercent) {
        weightRegistry.setLegacyWeightPercent(CustomerLookupCanaryController.MIGRATION_ID, legacyWeightPercent);
        return Map.of("legacyWeightPercent", legacyWeightPercent);
    }
}
