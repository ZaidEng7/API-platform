package com.company.platform.messaging.idempotent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

import java.util.UUID;

/**
 * Insert-first idempotency check: attempts to claim (eventId, consumerName)
 * via the unique constraint on {@link ProcessedEvent} and reports whether
 * this call won the race. Call this inside the SAME transaction as the
 * message processing itself, so a processing failure rolls back the claim
 * too and the event is retried rather than silently dropped.
 */
public class IdempotencyGuard {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean tryMarkProcessed(UUID eventId, String consumerName) {
        try {
            entityManager.persist(new ProcessedEvent(eventId, consumerName));
            entityManager.flush();
            return true;
        } catch (PersistenceException e) {
            entityManager.clear();
            return false;
        }
    }
}
