package com.company.aml.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestScreeningRequest(@NotNull UUID customerId) {
}
