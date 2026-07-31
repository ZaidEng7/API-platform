package com.company.document.api.dto;

import com.company.document.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UploadDocumentRequest(@NotNull UUID customerId, @NotNull DocumentType documentType,
                                     @NotBlank String storageReference) {
}
