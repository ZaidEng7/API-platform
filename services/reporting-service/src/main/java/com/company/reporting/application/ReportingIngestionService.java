package com.company.reporting.application;

import com.company.reporting.domain.AmlScreeningView;
import com.company.reporting.domain.DocumentView;
import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.KycCheckView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;
import com.company.reporting.domain.SubscriptionView;
import com.company.reporting.infrastructure.AmlScreeningViewJpaRepository;
import com.company.reporting.infrastructure.DocumentViewJpaRepository;
import com.company.reporting.infrastructure.FundNavViewJpaRepository;
import com.company.reporting.infrastructure.KycCheckViewJpaRepository;
import com.company.reporting.infrastructure.PaymentTransferViewJpaRepository;
import com.company.reporting.infrastructure.PortfolioViewJpaRepository;
import com.company.reporting.infrastructure.PositionViewJpaRepository;
import com.company.reporting.infrastructure.SubscriptionViewJpaRepository;
import com.company.reporting.messaging.dto.AmlScreeningCompletedPayload;
import com.company.reporting.messaging.dto.AmlScreeningFailedPayload;
import com.company.reporting.messaging.dto.AmlScreeningRequestedPayload;
import com.company.reporting.messaging.dto.DocumentReviewedPayload;
import com.company.reporting.messaging.dto.DocumentUploadedPayload;
import com.company.reporting.messaging.dto.FundNavUpdatedPayload;
import com.company.reporting.messaging.dto.FundRegisteredPayload;
import com.company.reporting.messaging.dto.KycCheckDecidedPayload;
import com.company.reporting.messaging.dto.KycCheckRequestedPayload;
import com.company.reporting.messaging.dto.PortfolioOpenedPayload;
import com.company.reporting.messaging.dto.PositionRecordedPayload;
import com.company.reporting.messaging.dto.SubscriptionCancelledPayload;
import com.company.reporting.messaging.dto.SubscriptionConfirmedPayload;
import com.company.reporting.messaging.dto.SubscriptionFailedPayload;
import com.company.reporting.messaging.dto.SubscriptionReservedPayload;
import com.company.reporting.messaging.dto.SubscriptionTimedOutPayload;
import com.company.reporting.messaging.dto.TransferFailedPayload;
import com.company.reporting.messaging.dto.TransferRequestedPayload;
import com.company.reporting.messaging.dto.TransferSettledPayload;
import com.company.platform.messaging.idempotent.IdempotencyGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The write side of this service's read-models — the only writer is event
 * consumption (guide §8.3: this service holds no System of Record of its
 * own, only read copies of Fund/Portfolio/Payment data). Every method is
 * idempotent on {@code eventId} via {@link IdempotencyGuard}, the same
 * dedup mechanism Audit Service's consumer already established.
 *
 * <p>{@code orElseGet(() -> new ...)} fallbacks below tolerate a later
 * lifecycle event (e.g. {@code fund.nav.updated}) arriving before its
 * "creation" event ({@code fund.definition.registered}) — RabbitMQ doesn't
 * guarantee cross-routing-key delivery order for a single queue with
 * multiple bindings, so a real consumer has to tolerate this rather than
 * assume publish order.
 */
@Service
public class ReportingIngestionService {

    private static final String CONSUMER_NAME = "reporting-service";

    private final FundNavViewJpaRepository fundNavViewRepository;
    private final PortfolioViewJpaRepository portfolioViewRepository;
    private final PositionViewJpaRepository positionViewRepository;
    private final PaymentTransferViewJpaRepository paymentTransferViewRepository;
    private final KycCheckViewJpaRepository kycCheckViewRepository;
    private final AmlScreeningViewJpaRepository amlScreeningViewRepository;
    private final DocumentViewJpaRepository documentViewRepository;
    private final SubscriptionViewJpaRepository subscriptionViewRepository;
    private final IdempotencyGuard idempotencyGuard;

    public ReportingIngestionService(FundNavViewJpaRepository fundNavViewRepository,
                                      PortfolioViewJpaRepository portfolioViewRepository,
                                      PositionViewJpaRepository positionViewRepository,
                                      PaymentTransferViewJpaRepository paymentTransferViewRepository,
                                      KycCheckViewJpaRepository kycCheckViewRepository,
                                      AmlScreeningViewJpaRepository amlScreeningViewRepository,
                                      DocumentViewJpaRepository documentViewRepository,
                                      SubscriptionViewJpaRepository subscriptionViewRepository,
                                      IdempotencyGuard idempotencyGuard) {
        this.fundNavViewRepository = fundNavViewRepository;
        this.portfolioViewRepository = portfolioViewRepository;
        this.positionViewRepository = positionViewRepository;
        this.paymentTransferViewRepository = paymentTransferViewRepository;
        this.kycCheckViewRepository = kycCheckViewRepository;
        this.amlScreeningViewRepository = amlScreeningViewRepository;
        this.documentViewRepository = documentViewRepository;
        this.subscriptionViewRepository = subscriptionViewRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional
    public void recordFundRegistered(UUID eventId, FundRegisteredPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        FundNavView view = fundNavViewRepository.findById(payload.fundCode())
                .orElseGet(() -> new FundNavView(payload.fundCode(), payload.registeredAt()));
        view.applyRegistration(payload.name(), payload.currency(), payload.registeredAt());
        fundNavViewRepository.save(view);
    }

    @Transactional
    public void recordFundNavUpdated(UUID eventId, FundNavUpdatedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        FundNavView view = fundNavViewRepository.findById(payload.fundCode())
                .orElseGet(() -> new FundNavView(payload.fundCode(), payload.fetchedAt()));
        view.applyNavUpdate(payload.navPerShare(), payload.asOfDate(), payload.fetchedAt());
        fundNavViewRepository.save(view);
    }

    @Transactional
    public void recordPortfolioOpened(UUID eventId, PortfolioOpenedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        portfolioViewRepository.save(new PortfolioView(payload.portfolioId(), payload.customerId(),
                payload.ownerId(), payload.name(), payload.currency(), payload.openedAt()));
    }

    @Transactional
    public void recordPositionRecorded(UUID eventId, PositionRecordedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        positionViewRepository.save(new PositionView(payload.positionId(), payload.portfolioId(),
                payload.fundCode(), payload.quantity(), payload.recordedAt()));
    }

    @Transactional
    public void recordTransferRequested(UUID eventId, TransferRequestedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        paymentTransferViewRepository.save(new PaymentTransferView(payload.transferId(), payload.customerId(),
                payload.amount(), payload.currency(), payload.requestedAt()));
    }

    @Transactional
    public void recordTransferSettled(UUID eventId, TransferSettledPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        PaymentTransferView view = paymentTransferViewRepository.findById(payload.transferId())
                .orElseGet(() -> new PaymentTransferView(payload.transferId(), payload.customerId(),
                        payload.amount(), payload.currency(), payload.settledAt()));
        view.settle(payload.settledAt());
        paymentTransferViewRepository.save(view);
    }

    @Transactional
    public void recordTransferFailed(UUID eventId, TransferFailedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        PaymentTransferView view = paymentTransferViewRepository.findById(payload.transferId())
                .orElseGet(() -> new PaymentTransferView(payload.transferId(), payload.customerId(), null, null,
                        payload.failedAt()));
        view.fail(payload.failureReason(), payload.failedAt());
        paymentTransferViewRepository.save(view);
    }

    @Transactional
    public void recordKycCheckRequested(UUID eventId, KycCheckRequestedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        kycCheckViewRepository.save(new KycCheckView(payload.checkId(), payload.customerId(), payload.requestedAt()));
    }

    @Transactional
    public void recordKycCheckDecided(UUID eventId, KycCheckDecidedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        KycCheckView view = kycCheckViewRepository.findById(payload.checkId())
                .orElseGet(() -> new KycCheckView(payload.checkId(), payload.customerId(), payload.decidedAt()));
        view.decide(payload.status(), payload.reason(), payload.decidedBy(), payload.decidedAt());
        kycCheckViewRepository.save(view);
    }

    @Transactional
    public void recordAmlScreeningRequested(UUID eventId, AmlScreeningRequestedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        amlScreeningViewRepository.save(new AmlScreeningView(payload.screeningId(), payload.customerId(),
                payload.requestedAt()));
    }

    @Transactional
    public void recordAmlScreeningCompleted(UUID eventId, AmlScreeningCompletedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        AmlScreeningView view = amlScreeningViewRepository.findById(payload.screeningId())
                .orElseGet(() -> new AmlScreeningView(payload.screeningId(), payload.customerId(),
                        payload.completedAt()));
        view.complete(payload.outcome(), payload.notes(), payload.completedAt());
        amlScreeningViewRepository.save(view);
    }

    @Transactional
    public void recordAmlScreeningFailed(UUID eventId, AmlScreeningFailedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        AmlScreeningView view = amlScreeningViewRepository.findById(payload.screeningId())
                .orElseGet(() -> new AmlScreeningView(payload.screeningId(), payload.customerId(),
                        payload.failedAt()));
        view.fail(payload.reason(), payload.failedAt());
        amlScreeningViewRepository.save(view);
    }

    @Transactional
    public void recordDocumentUploaded(UUID eventId, DocumentUploadedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        documentViewRepository.save(new DocumentView(payload.documentId(), payload.customerId(),
                payload.documentType(), payload.uploadedAt()));
    }

    @Transactional
    public void recordDocumentReviewed(UUID eventId, DocumentReviewedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        DocumentView view = documentViewRepository.findById(payload.documentId())
                .orElseGet(() -> new DocumentView(payload.documentId(), payload.customerId(), null,
                        payload.reviewedAt()));
        view.review(payload.status(), payload.notes(), payload.reviewedAt());
        documentViewRepository.save(view);
    }

    @Transactional
    public void recordSubscriptionReserved(UUID eventId, SubscriptionReservedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        subscriptionViewRepository.save(new SubscriptionView(payload.subscriptionId(), payload.customerId(),
                payload.portfolioId(), payload.fundCode(), payload.quantity(), payload.reservedAt()));
    }

    @Transactional
    public void recordSubscriptionFailed(UUID eventId, SubscriptionFailedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        SubscriptionView view = subscriptionViewRepository.findById(payload.subscriptionId())
                .orElseGet(() -> new SubscriptionView(payload.subscriptionId(), payload.customerId(),
                        payload.failedAt()));
        view.fail(payload.failureReason(), payload.failedAt());
        subscriptionViewRepository.save(view);
    }

    @Transactional
    public void recordSubscriptionConfirmed(UUID eventId, SubscriptionConfirmedPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        SubscriptionView view = subscriptionViewRepository.findById(payload.subscriptionId())
                .orElseGet(() -> new SubscriptionView(payload.subscriptionId(), payload.customerId(),
                        payload.portfolioId(), payload.fundCode(), payload.quantity(), null));
        view.confirm(payload.confirmedAt());
        subscriptionViewRepository.save(view);
    }

    @Transactional
    public void recordSubscriptionCancelled(UUID eventId, SubscriptionCancelledPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        SubscriptionView view = subscriptionViewRepository.findById(payload.subscriptionId())
                .orElseGet(() -> new SubscriptionView(payload.subscriptionId(), payload.customerId()));
        view.cancel(payload.cancelledAt());
        subscriptionViewRepository.save(view);
    }

    @Transactional
    public void recordSubscriptionTimedOut(UUID eventId, SubscriptionTimedOutPayload payload) {
        if (!idempotencyGuard.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }
        SubscriptionView view = subscriptionViewRepository.findById(payload.subscriptionId())
                .orElseGet(() -> new SubscriptionView(payload.subscriptionId(), payload.customerId()));
        view.timeout(payload.timedOutAt());
        subscriptionViewRepository.save(view);
    }
}
