package com.company.gateway.canary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Demonstrates guide §25 Phase 6's own migration method for one real
 * "conflict!" cell in the §8.3 SoR matrix — Customer/Party, owned by
 * Customer Service (target) but also by the legacy Onboarding+CRM
 * products (interim), reachable today only through {@code
 * integration/crm-adapter}. A consumer calling this single, stable
 * endpoint gets weighted-random shadowed to the legacy path (via
 * {@code crm-adapter}) or served from the migrated target (Customer
 * Service), controlled at runtime by {@link CanaryWeightRegistry} — no
 * consumer needs to know which backend actually answered, and rollback is
 * an instant weight change, not a redeploy.
 *
 * <p>Deliberately implemented as a plain proxying controller, not a
 * declarative Spring Cloud Gateway route: the stock {@code Weight} filter
 * reads its split from static YAML (fixed at startup), which can't deliver
 * "instant rollback = flag off" without a restart. This is a demonstration
 * of the mechanism the guide describes, not the real Web Portal/Mobile/
 * partner consumers Phase 6 actually migrates — none of those exist in
 * this repo (see {@code docs/roadmap.md} Phase 6).
 */
@RestController
@RequestMapping("/api/v1/customer-lookup")
public class CustomerLookupCanaryController {

    static final String MIGRATION_ID = "customer-lookup";

    private final WebClient webClient;
    private final CanaryWeightRegistry weightRegistry;
    private final String customerServiceBaseUrl;
    private final String crmAdapterBaseUrl;

    public CustomerLookupCanaryController(CanaryWeightRegistry weightRegistry,
                                           @Value("${CUSTOMER_SERVICE_URI:http://localhost:8081}") String customerServiceBaseUrl,
                                           @Value("${CRM_ADAPTER_URI:http://localhost:8084}") String crmAdapterBaseUrl) {
        this.webClient = WebClient.builder().build();
        this.weightRegistry = weightRegistry;
        this.customerServiceBaseUrl = customerServiceBaseUrl;
        this.crmAdapterBaseUrl = crmAdapterBaseUrl;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<String>> lookup(@PathVariable String id) {
        boolean useLegacy = ThreadLocalRandom.current().nextInt(100) < weightRegistry.getLegacyWeightPercent(MIGRATION_ID);
        String target = useLegacy ? crmAdapterBaseUrl + "/api/v1/crm-customers/" + id
                : customerServiceBaseUrl + "/api/v1/customers/" + id;
        String targetName = useLegacy ? "crm-adapter" : "customer-service";

        return webClient.get()
                .uri(target)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> ResponseEntity.status(response.statusCode())
                                .header("X-Canary-Target", targetName)
                                .body(body)));
    }
}
