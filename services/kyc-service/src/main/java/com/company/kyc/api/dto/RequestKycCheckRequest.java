package com.company.kyc.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestKycCheckRequest(@NotNull UUID customerId) {
}
