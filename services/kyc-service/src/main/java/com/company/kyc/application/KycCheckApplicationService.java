package com.company.kyc.application;

import com.company.kyc.domain.KycCheck;
import com.company.kyc.domain.KycCheckStatus;
import com.company.kyc.domain.event.KycCheckDecided;
import com.company.kyc.domain.event.KycCheckRequested;
import com.company.kyc.infrastructure.KycCheckJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.security.CurrentUser;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.security.CurrentUser;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enforces the same BOLA/IDOR ownership rule as Portfolio/Investment
 * Service (guide §12.2): an 'investor' caller may only read their own KYC
 * checks (customerId compared to the JWT {@code sub} claim); staff roles
 * see any customer's.
 */
@Service
public class KycCheckApplicationService {

    private static final List<String> STAFF_ROLES = List.of("OPERATIONS", "COMPLIANCE", "CUSTOMER_SERVICE", "AUDITOR");

    private static final String REQUESTED_EVENT_TYPE = "customer.kyc.requested";
    private static final String PRODUCER = "kyc-service";

    private final KycCheckJpaRepository kycCheckRepository;
    private final OutboxEventStore outboxEventStore;

    public KycCheckApplicationService(KycCheckJpaRepository kycCheckRepository, OutboxEventStore outboxEventStore) {
        this.kycCheckRepository = kycCheckRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public KycCheck getById(UUID id) {
        KycCheck check = kycCheckRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KYC-4041", "KYC check not found: " + id));
        enforceOwnership(check);
        return check;
    }

    /** @throws ApiException 403 if an investor-only caller queries another customer's checks. */
    @Transactional(readOnly = true)
    public Page<KycCheck> listByCustomer(UUID customerId, Pageable pageable) {
        if (!isStaff() && !customerId.toString().equals(CurrentUser.subject().orElse(null))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC-4030", "You do not have access to that customer's KYC checks");
        }
        return kycCheckRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Writes the outbox row in the SAME transaction as the check insert
     * (guide §8.4: no dual-write without outbox).
     */
    @Transactional
    public KycCheck requestCheck(UUID customerId) {
        Instant now = Instant.now();
        KycCheck check = kycCheckRepository.save(new KycCheck(UUID.randomUUID(), customerId, now));

        var payload = new KycCheckRequested(check.getId(), check.getCustomerId(), check.getCreatedAt());
        publish(REQUESTED_EVENT_TYPE, check.getId(), payload, check.getCreatedAt());

        return check;
    }

    /**
     * @throws ApiException 409 if this check was already decided — a KYC
     *                       decision is made once; a changed mind requests a
     *                       new check rather than mutating a prior one.
     */
    @Transactional
    public KycCheck decide(UUID id, KycCheckStatus outcome, String reason) {
        if (outcome != KycCheckStatus.APPROVED && outcome != KycCheckStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC-4000", "Decision outcome must be APPROVED or REJECTED");
        }

        KycCheck check = getById(id);
        String decidedBy = CurrentUser.subject().orElse(null);
        try {
            check.decide(outcome, reason, decidedBy);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "KYC-4090", e.getMessage());
        }

        String eventType = outcome == KycCheckStatus.APPROVED ? "customer.kyc.approved" : "customer.kyc.rejected";
        var payload = new KycCheckDecided(check.getId(), check.getCustomerId(), check.getStatus(), check.getReason(),
                check.getDecidedBy(), check.getUpdatedAt());
        publish(eventType, check.getId(), payload, check.getUpdatedAt());

        return check;
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
        outboxEventStore.write("KycCheck", aggregateId.toString(), eventType, envelope, correlationId, PRODUCER);
    }

    private void enforceOwnership(KycCheck check) {
        if (isStaff()) {
            return;
        }
        String callerId = CurrentUser.subject().orElse(null);
        if (!check.getCustomerId().toString().equals(callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC-4030", "You do not have access to this KYC check");
        }
    }

    private boolean isStaff() {
        return STAFF_ROLES.stream().anyMatch(CurrentUser::hasRole);
    }
}
