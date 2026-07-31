package com.company.onboardingadapter.legacy;

import com.company.onboardingadapter.api.dto.OnboardingApplicationRequest;
import com.company.onboardingadapter.api.dto.OnboardingApplicationResponse;
import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRecord;
import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRequest;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * The anti-corruption boundary (guide §9.1), both directions this time: the
 * legacy system's SCREAMING_SNAKE_CASE fields, bare "yyyyMMdd" date string,
 * and single-char status codes never reach the canonical API, and the
 * canonical request never reaches the legacy system in ISO-8601/business
 * shape.
 */
@Component
public class LegacyOnboardingTranslator {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public LegacyOnboardingApplicationRequest toLegacyRequest(OnboardingApplicationRequest canonical) {
        return new LegacyOnboardingApplicationRequest(
                canonical.fullName(),
                canonical.email(),
                canonical.dateOfBirth().format(LEGACY_DATE_FORMAT));
    }

    public OnboardingApplicationResponse toCanonicalResponse(LegacyOnboardingApplicationRecord legacy) {
        return new OnboardingApplicationResponse(legacy.applRefNo(), translateStatus(legacy.applStatusCd()));
    }

    private OnboardingApplicationResponse.ApplicationStatus translateStatus(String applStatusCd) {
        return switch (applStatusCd) {
            case "P" -> OnboardingApplicationResponse.ApplicationStatus.PENDING;
            case "A" -> OnboardingApplicationResponse.ApplicationStatus.APPROVED;
            case "R" -> OnboardingApplicationResponse.ApplicationStatus.REJECTED;
            default -> throw new IllegalStateException("Unknown legacy APPL_STATUS_CD: " + applStatusCd);
        };
    }
}
