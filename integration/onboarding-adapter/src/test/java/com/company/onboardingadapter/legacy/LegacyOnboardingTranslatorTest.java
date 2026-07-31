package com.company.onboardingadapter.legacy;

import com.company.onboardingadapter.api.dto.OnboardingApplicationRequest;
import com.company.onboardingadapter.api.dto.OnboardingApplicationResponse;
import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyOnboardingTranslatorTest {

    private final LegacyOnboardingTranslator translator = new LegacyOnboardingTranslator();

    @Test
    void translatesCanonicalRequestToLegacyShape() {
        var canonical = new OnboardingApplicationRequest("Ada Lovelace", "ada@example.com", LocalDate.of(1990, 3, 5));

        var legacy = translator.toLegacyRequest(canonical);

        assertThat(legacy.applFullNm()).isEqualTo("Ada Lovelace");
        assertThat(legacy.applEmail()).isEqualTo("ada@example.com");
        assertThat(legacy.dobYyyymmdd()).isEqualTo("19900305");
    }

    @Test
    void translatesLegacyPendingResponse() {
        var legacy = new LegacyOnboardingApplicationRecord("REF-42", "P");

        var canonical = translator.toCanonicalResponse(legacy);

        assertThat(canonical.applicationId()).isEqualTo("REF-42");
        assertThat(canonical.status()).isEqualTo(OnboardingApplicationResponse.ApplicationStatus.PENDING);
    }

    @Test
    void translatesLegacyApprovedAndRejectedResponses() {
        assertThat(translator.toCanonicalResponse(new LegacyOnboardingApplicationRecord("REF-1", "A")).status())
                .isEqualTo(OnboardingApplicationResponse.ApplicationStatus.APPROVED);
        assertThat(translator.toCanonicalResponse(new LegacyOnboardingApplicationRecord("REF-2", "R")).status())
                .isEqualTo(OnboardingApplicationResponse.ApplicationStatus.REJECTED);
    }

    @Test
    void rejectsUnknownStatusCode() {
        var legacy = new LegacyOnboardingApplicationRecord("REF-3", "X");

        assertThatThrownBy(() -> translator.toCanonicalResponse(legacy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }
}
