package com.company.aml.application;

import com.company.aml.domain.AmlScreening;
import com.company.aml.domain.ScreeningOutcome;
import com.company.aml.domain.event.AmlScreeningCompleted;
import com.company.aml.domain.event.AmlScreeningFailed;
import com.company.aml.domain.event.AmlScreeningRequested;
import com.company.aml.infrastructure.AmlScreeningJpaRepository;
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
public class AmlScreeningApplicationService {

    private static final String REQUESTED_EVENT_TYPE = "customer.aml.requested";
    private static final String FAILED_EVENT_TYPE = "customer.aml.failed";
    private static final String PRODUCER = "aml-service";

    private final AmlScreeningJpaRepository amlScreeningRepository;
    private final OutboxEventStore outboxEventStore;

    public AmlScreeningApplicationService(AmlScreeningJpaRepository amlScreeningRepository,
                                           OutboxEventStore outboxEventStore) {
        this.amlScreeningRepository = amlScreeningRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public AmlScreening getById(UUID id) {
        return amlScreeningRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AML-4041", "AML screening not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<AmlScreening> listByCustomer(UUID customerId, Pageable pageable) {
        return amlScreeningRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Writes the outbox row in the SAME transaction as the screening insert
     * (guide §8.4: no dual-write without outbox). Returns immediately with
     * status IN_PROGRESS — guide §10.3: never hold the HTTP connection open
     * waiting on screening completion.
     */
    @Transactional
    public AmlScreening requestScreening(UUID customerId) {
        Instant now = Instant.now();
        AmlScreening screening = amlScreeningRepository.save(new AmlScreening(UUID.randomUUID(), customerId, now));

        var payload = new AmlScreeningRequested(screening.getId(), screening.getCustomerId(), screening.getCreatedAt());
        publish(REQUESTED_EVENT_TYPE, screening.getId(), payload, screening.getCreatedAt());

        return screening;
    }

    /** @throws ApiException 409 if this screening is no longer IN_PROGRESS. */
    @Transactional
    public AmlScreening complete(UUID id, ScreeningOutcome outcome, String notes) {
        AmlScreening screening = getById(id);
        try {
            screening.complete(outcome, notes);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "AML-4090", e.getMessage());
        }

        String eventType = outcome == ScreeningOutcome.CLEAR ? "customer.aml.cleared" : "customer.aml.flagged";
        var payload = new AmlScreeningCompleted(screening.getId(), screening.getCustomerId(), screening.getOutcome(),
                screening.getNotes(), screening.getCompletedAt());
        publish(eventType, screening.getId(), payload, screening.getCompletedAt());

        return screening;
    }

    /** @throws ApiException 409 if this screening is no longer IN_PROGRESS. */
    @Transactional
    public AmlScreening fail(UUID id, String reason) {
        AmlScreening screening = getById(id);
        try {
            screening.fail(reason);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "AML-4090", e.getMessage());
        }

        var payload = new AmlScreeningFailed(screening.getId(), screening.getCustomerId(), screening.getNotes(),
                screening.getUpdatedAt());
        publish(FAILED_EVENT_TYPE, screening.getId(), payload, screening.getUpdatedAt());

        return screening;
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
        outboxEventStore.write("AmlScreening", aggregateId.toString(), eventType, envelope, correlationId, PRODUCER);
    }
}
