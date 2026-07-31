package com.company.onboardingadapter.legacy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TEMPLATE — the legacy onboarding system's actual request shape
 * (SCREAMING_SNAKE_CASE, date-of-birth as a bare "yyyyMMdd" string instead
 * of ISO-8601). Deliberately fictional — see ../../../../../../../README.md.
 */
public record LegacyOnboardingApplicationRequest(
        @JsonProperty("APPL_FULL_NM") String applFullNm,
        @JsonProperty("APPL_EMAIL") String applEmail,
        @JsonProperty("DOB_YYYYMMDD") String dobYyyymmdd) {
}
