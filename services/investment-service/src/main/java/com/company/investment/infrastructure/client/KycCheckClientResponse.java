package com.company.investment.infrastructure.client;

/** Mirrors the {@code status} field of KYC Service's own {@code KycCheckResponse} — just what this client needs. */
public record KycCheckClientResponse(String status) {
}
