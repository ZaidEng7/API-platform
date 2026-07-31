package com.company.document.domain.event;

import com.company.document.domain.DocumentType;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.document.uploaded} (guide §22 naming). No file content — see {@link com.company.document.domain.Document}. */
public record DocumentUploaded(UUID documentId, UUID customerId, DocumentType documentType, Instant uploadedAt) {
}
