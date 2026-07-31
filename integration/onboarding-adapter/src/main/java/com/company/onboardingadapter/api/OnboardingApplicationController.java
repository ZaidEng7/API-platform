package com.company.onboardingadapter.api;

import com.company.onboardingadapter.api.dto.OnboardingApplicationRequest;
import com.company.onboardingadapter.api.dto.OnboardingApplicationResponse;
import com.company.onboardingadapter.legacy.LegacyOnboardingClient;
import com.company.onboardingadapter.legacy.LegacyOnboardingTranslator;
import com.company.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The clean, business-language side of the adapter (guide §9.1) — same API
 * standards as any other service (§9.2): standard envelope, RFC 7807 errors
 * (via common-web, unchanged from the rest of the platform).
 */
@RestController
@RequestMapping("/api/v1/onboarding-applications")
public class OnboardingApplicationController {

    private final LegacyOnboardingClient legacyOnboardingClient;
    private final LegacyOnboardingTranslator translator;

    public OnboardingApplicationController(LegacyOnboardingClient legacyOnboardingClient, LegacyOnboardingTranslator translator) {
        this.legacyOnboardingClient = legacyOnboardingClient;
        this.translator = translator;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingApplicationResponse>> submit(@Valid @RequestBody OnboardingApplicationRequest request) {
        var legacyRequest = translator.toLegacyRequest(request);
        var legacyRecord = legacyOnboardingClient.submit(legacyRequest);
        var response = translator.toCanonicalResponse(legacyRecord);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
