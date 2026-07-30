package com.company.platform.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Uses {@link EntityManager} directly rather than a Spring Data repository
 * interface: a library-provided {@code @EnableJpaRepositories} would
 * otherwise take over repository scanning for the whole consuming service
 * and silently stop its own repositories from being picked up. Field-based
 * {@code @PersistenceContext} injection is used (not a constructor
 * parameter) since Spring Boot doesn't register a plain {@code EntityManager}
 * bean — only {@code @PersistenceContext} resolves the shared, transaction-
 * bound instance.
 */
public class OutboxEventStore {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    public OutboxEventStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the outbox row in the SAME transaction as the caller's domain
     * change (guide §8.4: no dual-write without outbox). Fails fast if
     * called outside an active transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, String aggregateId, String eventType, Object payload,
                       String correlationId, String producer) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Outbox payload is not serializable: " + eventType, e);
        }

        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, json,
                Instant.now(), correlationId, producer, 1);
        entityManager.persist(event);
    }

    @SuppressWarnings("unchecked")
    public List<OutboxEvent> findPendingBatch(int limit) {
        return entityManager.createQuery(
                        "SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
                .setParameter("status", OutboxEventStatus.PENDING)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional
    public void markPublished(UUID id) {
        OutboxEvent event = entityManager.find(OutboxEvent.class, id);
        if (event != null) {
            event.markPublished();
        }
    }

    @Transactional
    public void recordFailure(UUID id, int maxAttempts) {
        OutboxEvent event = entityManager.find(OutboxEvent.class, id);
        if (event != null) {
            event.recordFailure(maxAttempts);
        }
    }
}
