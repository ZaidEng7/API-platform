package com.company.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code paymentMethodToken} must look like a PSP-issued token
 * ({@code tok_...}), never a raw card/account number — the guide's own
 * PCI-DSS scope-isolation rule (§3.1/§12.5: "card data never enters the
 * platform") enforced structurally at the API boundary, not just by
 * convention. A real integration would validate against whichever PSP's
 * actual token format is in use; this is a template pattern.
 */
public record RequestTransferRequest(
        @NotNull UUID customerId,
        @NotNull UUID ownerId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 currency code") String currency,
        @NotBlank @Pattern(regexp = "tok_.+", message = "must be a PSP token (tok_...), not raw card/account data") String paymentMethodToken,
        String reference) {
}
