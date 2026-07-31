package com.company.document.domain.event;

import com.company.document.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.document.verified}/{@code customer.document.rejected} (guide §22 naming). */
public record DocumentReviewed(UUID documentId, UUID customerId, DocumentStatus status, String notes, Instant reviewedAt) {
}
