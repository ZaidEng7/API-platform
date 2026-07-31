package com.company.investment.infrastructure.client;

/** Mirrors the {@code status}/{@code outcome} fields of AML Service's own {@code ScreeningResponse}. */
public record AmlScreeningClientResponse(String status, String outcome) {
}
