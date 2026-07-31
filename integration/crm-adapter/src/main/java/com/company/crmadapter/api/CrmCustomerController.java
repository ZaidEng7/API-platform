package com.company.crmadapter.api;

import com.company.crmadapter.api.dto.CrmCustomerResponse;
import com.company.crmadapter.legacy.LegacyCrmClient;
import com.company.crmadapter.legacy.LegacyCrmCustomerTranslator;
import com.company.platform.web.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The clean, business-language side of the adapter (guide §9.1) — same API
 * standards as any other service (§9.2): standard envelope, RFC 7807 errors
 * (via common-web, unchanged from the rest of the platform).
 */
@RestController
@RequestMapping("/api/v1/crm-customers")
public class CrmCustomerController {

    private final LegacyCrmClient legacyCrmClient;
    private final LegacyCrmCustomerTranslator translator;

    public CrmCustomerController(LegacyCrmClient legacyCrmClient, LegacyCrmCustomerTranslator translator) {
        this.legacyCrmClient = legacyCrmClient;
        this.translator = translator;
    }

    @GetMapping("/{id}")
    public ApiResponse<CrmCustomerResponse> getById(@PathVariable String id) {
        var legacyRecord = legacyCrmClient.getCustomer(id);
        return ApiResponse.of(translator.toCanonical(legacyRecord));
    }
}
