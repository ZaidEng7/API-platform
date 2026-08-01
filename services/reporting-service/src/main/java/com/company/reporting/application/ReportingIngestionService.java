package com.company.reporting.application;

import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;
import com.company.reporting.infrastructure.FundNavViewJpaRepository;
import com.company.reporting.infrastructure.PaymentTransferViewJpaRepository;
import com.company.reporting.infrastructure.PortfolioViewJpaRepository;
import com.company.reporting.infrastructure.PositionViewJpaRepository;
import com.company.reporting.messaging.dto.FundNavUpdatedPayload;
import com.company.reporting.messaging.dto.FundRegisteredPayload;
import com.company.reporting.messaging.dto.PortfolioOpenedPayload;
import com.company.reporting.messaging.dto.PositionRecordedPayload;
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
    private final IdempotencyGuard idempotencyGuard;

    public ReportingIngestionService(FundNavViewJpaRepository fundNavViewRepository,
                                      PortfolioViewJpaRepository portfolioViewRepository,
                                      PositionViewJpaRepository positionViewRepository,
                                      PaymentTransferViewJpaRepository paymentTransferViewRepository,
                                      IdempotencyGuard idempotencyGuard) {
        this.fundNavViewRepository = fundNavViewRepository;
        this.portfolioViewRepository = portfolioViewRepository;
        this.positionViewRepository = positionViewRepository;
        this.paymentTransferViewRepository = paymentTransferViewRepository;
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
}
