package com.company.crmadapter.legacy;

import com.company.crmadapter.legacy.dto.LegacyCrmCustomerRecord;
import com.company.platform.web.exception.ApiException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TEMPLATE — the anti-corruption boundary (guide §9.1). Everything legacy
 * (field names, status codes, Y/N flags) stops here; {@link
 * com.company.crmadapter.api.CrmCustomerController} only ever sees the
 * canonical shape. Resilience config (§9.4) lives in application.yml under
 * the "legacyCrm" instance name — connect/read timeouts are on the
 * RestClient itself (see LegacyCrmClientConfig), retry/circuit-breaker/
 * bulkhead are these annotations. This is a read (GET), so blind retry is
 * safe — never apply @Retry to a write against a real legacy system without
 * confirming idempotency (§9.4).
 */
@Component
public class LegacyCrmClient {

    private final RestClient legacyCrmRestClient;

    public LegacyCrmClient(RestClient legacyCrmRestClient) {
        this.legacyCrmRestClient = legacyCrmRestClient;
    }

    @CircuitBreaker(name = "legacyCrm", fallbackMethod = "fallback")
    @Retry(name = "legacyCrm", fallbackMethod = "fallback")
    @Bulkhead(name = "legacyCrm", fallbackMethod = "fallback")
    public LegacyCrmCustomerRecord getCustomer(String custId) {
        return legacyCrmRestClient.get()
                .uri("/crm/v1/customers/{custId}", custId)
                .retrieve()
                .body(LegacyCrmCustomerRecord.class);
    }

    @SuppressWarnings("unused") // invoked reflectively by resilience4j
    private LegacyCrmCustomerRecord fallback(String custId, Throwable cause) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CRM-5031",
                "Legacy CRM is unavailable: " + cause.getMessage());
    }
}
