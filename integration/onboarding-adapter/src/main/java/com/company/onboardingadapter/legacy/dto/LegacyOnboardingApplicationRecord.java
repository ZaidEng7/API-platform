package com.company.onboardingadapter.legacy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TEMPLATE — the legacy system's response shape (a reference number instead
 * of a proper ID field name, single-char status codes). Deliberately
 * fictional — see ../../../../../../../README.md.
 */
public record LegacyOnboardingApplicationRecord(
        @JsonProperty("APPL_REF_NO") String applRefNo,
        @JsonProperty("APPL_STATUS_CD") String applStatusCd) {
}
