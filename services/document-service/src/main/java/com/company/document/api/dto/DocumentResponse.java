package com.company.document.api.dto;

import com.company.document.domain.DocumentStatus;
import com.company.document.domain.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID customerId, DocumentType documentType, String storageReference,
                                DocumentStatus status, String notes, Instant createdAt, Instant updatedAt) {
}
