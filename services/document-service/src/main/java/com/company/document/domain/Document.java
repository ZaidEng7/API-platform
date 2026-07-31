package com.company.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata for a Party — guide §8.3 lists this service as the
 * target SoR for "Documents" with read copies "— (references only)": no
 * other service keeps a copy, and this service itself stores only a
 * {@code storageReference} pointer, never the file's actual bytes/content.
 * That's a deliberate scope decision, not an oversight — actual blob
 * storage (S3/MinIO/the legacy DMS product) is a separate concern this
 * service doesn't own; {@code storageReference} is whatever pointer that
 * eventual storage layer or DMS adapter (guide §9, not built yet — Phase 1
 * legacy integration is still deferred) hands back. Never logging document
 * *contents* (guide §14) is structural here, not policy: this entity never
 * holds content in the first place. {@code customerId} references
 * Customer Service's Party by id only (§8.1).
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private String storageReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    public Document(UUID id, UUID customerId, DocumentType documentType, String storageReference, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.documentType = documentType;
        this.storageReference = storageReference;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** @throws IllegalStateException if this document isn't still UPLOADED. */
    public void verify(String notes) {
        requireUploaded();
        this.status = DocumentStatus.VERIFIED;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    /** @throws IllegalStateException if this document isn't still UPLOADED. */
    public void reject(String notes) {
        requireUploaded();
        this.status = DocumentStatus.REJECTED;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    private void requireUploaded() {
        if (this.status != DocumentStatus.UPLOADED) {
            throw new IllegalStateException("Document " + id + " was already reviewed (" + this.status + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
