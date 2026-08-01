package com.company.payment.application;

import com.company.payment.domain.Transfer;
import com.company.payment.domain.event.TransferFailed;
import com.company.payment.domain.event.TransferRequested;
import com.company.payment.domain.event.TransferSettled;
import com.company.payment.infrastructure.TransferJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.security.CurrentUser;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * No real PSP integration exists (Phase 4 never built a payment adapter),
 * so settlement is confirmed via a human/finance-ops action rather than an
 * outbound call — see {@link Transfer}'s Javadoc. Because there's no
 * outbound PSP call in this service's own code, guide §12.4's "never
 * blind-retry a payment POST" doesn't have a call site to apply to here;
 * it's documented in this module's README as guidance for whichever real
 * PSP adapter eventually gets built.
 */
@Service
public class TransferApplicationService {

    private static final List<String> STAFF_ROLES =
            List.of("OPERATIONS", "PORTFOLIO_MANAGER", "AUDITOR", "COMPLIANCE");

    private static final String REQUESTED_EVENT_TYPE = "payment.transfer.requested";
    private static final String SETTLED_EVENT_TYPE = "payment.transfer.settled";
    private static final String FAILED_EVENT_TYPE = "payment.transfer.failed";
    private static final String PRODUCER = "payment-service";

    private final TransferJpaRepository transferRepository;
    private final OutboxEventStore outboxEventStore;

    public TransferApplicationService(TransferJpaRepository transferRepository, OutboxEventStore outboxEventStore) {
        this.transferRepository = transferRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public Transfer getById(UUID id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAY-4041", "Transfer not found: " + id));
        enforceOwnership(transfer);
        return transfer;
    }

    /** @throws ApiException 403 if an investor-only caller queries another investor's {@code ownerId}. */
    @Transactional(readOnly = true)
    public Page<Transfer> listByOwner(UUID ownerId, Pageable pageable) {
        if (!isStaff() && !ownerId.toString().equals(CurrentUser.subject().orElse(null))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAY-4030", "You do not have access to that owner's transfers");
        }
        return transferRepository.findByOwnerId(ownerId, pageable);
    }

    /**
     * Idempotent on {@code idempotencyKey} (guide §12.3: financial-effect
     * POSTs require a client-generated {@code Idempotency-Key}) — a
     * replayed request with the same key returns the original transfer
     * rather than creating a duplicate. Returns immediately with status
     * PENDING — guide §10.3: never hold the HTTP connection open waiting
     * on settlement.
     */
    @Transactional
    public Transfer requestTransfer(String idempotencyKey, UUID customerId, UUID ownerId, BigDecimal amount,
                                     String currency, String paymentMethodToken, String reference) {
        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        Transfer transfer = transferRepository.save(new Transfer(UUID.randomUUID(), idempotencyKey, customerId,
                ownerId, amount, currency, paymentMethodToken, reference, now));

        var payload = new TransferRequested(transfer.getId(), transfer.getCustomerId(), transfer.getAmount(),
                transfer.getCurrency(), now);
        publish(REQUESTED_EVENT_TYPE, transfer.getId(), payload, now);

        return transfer;
    }

    /** @throws ApiException 409 if this transfer is no longer PENDING. */
    @Transactional
    public Transfer settle(UUID id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAY-4041", "Transfer not found: " + id));
        try {
            transfer.settle();
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "PAY-4090", e.getMessage());
        }

        var payload = new TransferSettled(transfer.getId(), transfer.getCustomerId(), transfer.getAmount(),
                transfer.getCurrency(), transfer.getSettledAt());
        publish(SETTLED_EVENT_TYPE, transfer.getId(), payload, transfer.getSettledAt());

        return transfer;
    }

    /** @throws ApiException 409 if this transfer is no longer PENDING. */
    @Transactional
    public Transfer fail(UUID id, String reason) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAY-4041", "Transfer not found: " + id));
        try {
            transfer.fail(reason);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "PAY-4090", e.getMessage());
        }

        var payload = new TransferFailed(transfer.getId(), transfer.getCustomerId(), transfer.getFailureReason(),
                transfer.getUpdatedAt());
        publish(FAILED_EVENT_TYPE, transfer.getId(), payload, transfer.getUpdatedAt());

        return transfer;
    }

    private void enforceOwnership(Transfer transfer) {
        if (isStaff()) {
            return;
        }
        String callerId = CurrentUser.subject().orElse(null);
        if (callerId == null || !callerId.equals(transfer.getOwnerId().toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAY-4030", "You do not have access to this transfer");
        }
    }

    private boolean isStaff() {
        return STAFF_ROLES.stream().anyMatch(CurrentUser::hasRole);
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
        outboxEventStore.write("Transfer", aggregateId.toString(), eventType, envelope, correlationId, PRODUCER);
    }
}
