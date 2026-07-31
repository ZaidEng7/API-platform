package com.company.document.application;

import com.company.document.domain.Document;
import com.company.document.domain.DocumentType;
import com.company.document.domain.event.DocumentReviewed;
import com.company.document.domain.event.DocumentUploaded;
import com.company.document.infrastructure.DocumentJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DocumentApplicationService {

    private static final String UPLOADED_EVENT_TYPE = "customer.document.uploaded";
    private static final String PRODUCER = "document-service";

    private final DocumentJpaRepository documentRepository;
    private final OutboxEventStore outboxEventStore;

    public DocumentApplicationService(DocumentJpaRepository documentRepository, OutboxEventStore outboxEventStore) {
        this.documentRepository = documentRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOC-4041", "Document not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Document> listByCustomer(UUID customerId, Pageable pageable) {
        return documentRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Writes the outbox row in the SAME transaction as the document insert
     * (guide §8.4: no dual-write without outbox). {@code storageReference}
     * is an opaque pointer supplied by the caller — this service never
     * accepts or stores actual file content (see {@link Document}'s Javadoc).
     */
    @Transactional
    public Document upload(UUID customerId, DocumentType documentType, String storageReference) {
        Instant now = Instant.now();
        Document document = documentRepository.save(
                new Document(UUID.randomUUID(), customerId, documentType, storageReference, now));

        var payload = new DocumentUploaded(document.getId(), document.getCustomerId(), document.getDocumentType(),
                document.getCreatedAt());
        publish(UPLOADED_EVENT_TYPE, document.getId(), payload, document.getCreatedAt());

        return document;
    }

    /** @throws ApiException 409 if this document was already reviewed. */
    @Transactional
    public Document verify(UUID id, String notes) {
        Document document = getById(id);
        try {
            document.verify(notes);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DOC-4090", e.getMessage());
        }

        var payload = new DocumentReviewed(document.getId(), document.getCustomerId(), document.getStatus(),
                document.getNotes(), document.getUpdatedAt());
        publish("customer.document.verified", document.getId(), payload, document.getUpdatedAt());

        return document;
    }

    /** @throws ApiException 409 if this document was already reviewed. */
    @Transactional
    public Document reject(UUID id, String notes) {
        Document document = getById(id);
        try {
            document.reject(notes);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DOC-4090", e.getMessage());
        }

        var payload = new DocumentReviewed(document.getId(), document.getCustomerId(), document.getStatus(),
                document.getNotes(), document.getUpdatedAt());
        publish("customer.document.rejected", document.getId(), payload, document.getUpdatedAt());

        return document;
    }

    /**
     * The full {@link EventEnvelope} is stored as the outbox payload, not
     * just the raw domain data, so consumers see the mandatory envelope
     * shape (guide §22) on the wire once relayed.
     */
    private <T> void publish(String eventType, UUID aggregateId, T payload, Instant occurredAt) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        var envelope = new EventEnvelope<>(UUID.randomUUID(), eventType, occurredAt, correlationId, PRODUCER, 1,
                payload);
        outboxEventStore.write("Document", aggregateId.toString(), eventType, envelope, correlationId, PRODUCER);
    }
}
