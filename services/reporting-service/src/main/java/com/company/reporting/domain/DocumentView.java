package com.company.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read copy of Document Service's own "Document" (guide §8.3 SoR matrix).
 * Created on {@code customer.document.uploaded}, updated to a terminal state
 * on {@code .verified}/{@code .rejected}. {@code documentType} is stored as
 * a plain String (same as {@code currency} on {@link PaymentTransferView})
 * rather than a mirrored enum — this read-model has no business logic keyed
 * off specific document types, so there's no need for a reporting-owned enum.
 */
@Entity
@Table(name = "document_view")
public class DocumentView {

    @Id
    private UUID documentId;

    @Column(nullable = false)
    private UUID customerId;

    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentReportStatus status;

    private String notes;

    @Column(nullable = false)
    private Instant uploadedAt;

    private Instant reviewedAt;

    protected DocumentView() {
    }

    public DocumentView(UUID documentId, UUID customerId, String documentType, Instant uploadedAt) {
        this.documentId = documentId;
        this.customerId = customerId;
        this.documentType = documentType;
        this.status = DocumentReportStatus.UPLOADED;
        this.uploadedAt = uploadedAt;
    }

    public void review(DocumentReportStatus status, String notes, Instant reviewedAt) {
        this.status = status;
        this.notes = notes;
        this.reviewedAt = reviewedAt;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public DocumentReportStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
