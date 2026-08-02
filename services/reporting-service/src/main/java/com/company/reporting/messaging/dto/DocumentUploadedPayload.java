package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Document Service's own {@code DocumentUploaded} event payload — {@code customer.document.uploaded}. */
public record DocumentUploadedPayload(UUID documentId, UUID customerId, String documentType, Instant uploadedAt) {
}
