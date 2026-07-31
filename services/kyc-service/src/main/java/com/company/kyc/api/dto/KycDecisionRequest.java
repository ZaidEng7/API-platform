package com.company.kyc.api.dto;

import com.company.kyc.domain.KycCheckStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code outcome} must be APPROVED or REJECTED — enforced service-side (KYC-4000 otherwise). */
public record KycDecisionRequest(@NotNull KycCheckStatus outcome, @NotBlank String reason) {
}
