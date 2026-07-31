package com.company.fundmgmtadapter.api;

import com.company.fundmgmtadapter.api.dto.FundNavResponse;
import com.company.fundmgmtadapter.legacy.FundNavProvider;
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
@RequestMapping("/api/v1/funds")
public class FundNavController {

    private final FundNavProvider fundNavProvider;

    public FundNavController(FundNavProvider fundNavProvider) {
        this.fundNavProvider = fundNavProvider;
    }

    @GetMapping("/{fundCode}/nav")
    public ApiResponse<FundNavResponse> getNav(@PathVariable String fundCode) {
        return ApiResponse.of(fundNavProvider.getNav(fundCode));
    }
}
