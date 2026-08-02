package com.company.reporting.messaging;

import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.reporting.application.ReportingIngestionService;
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
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Deserializes the full {@link EventEnvelope} each producer publishes (see
 * every Phase 5 service's own {@code publish()} method) and dispatches by
 * routing key. This queue is now bound to Fund, Portfolio, Payment, KYC,
 * AML, Document, and Subscription routing patterns (see
 * {@link ReportingMessagingConfig}) — the {@code default} branch below only
 * catches events outside those bindings, which shouldn't normally reach this
 * queue at all, so it's defensive, not load-bearing.
 */
@Component
public class DomainEventReportingListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventReportingListener.class);

    private final ObjectMapper objectMapper;
    private final ReportingIngestionService ingestionService;

    public DomainEventReportingListener(ObjectMapper objectMapper, ReportingIngestionService ingestionService) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
    }

    @RabbitListener(queues = ReportingMessagingConfig.QUEUE_NAME)
    public void onDomainEvent(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        switch (routingKey) {
            case "fund.definition.registered" -> {
                var envelope = readEnvelope(body, FundRegisteredPayload.class);
                ingestionService.recordFundRegistered(envelope.eventId(), envelope.payload());
            }
            case "fund.nav.updated" -> {
                var envelope = readEnvelope(body, FundNavUpdatedPayload.class);
                ingestionService.recordFundNavUpdated(envelope.eventId(), envelope.payload());
            }
            case "portfolio.account.opened" -> {
                var envelope = readEnvelope(body, PortfolioOpenedPayload.class);
                ingestionService.recordPortfolioOpened(envelope.eventId(), envelope.payload());
            }
            case "portfolio.position.recorded" -> {
                var envelope = readEnvelope(body, PositionRecordedPayload.class);
                ingestionService.recordPositionRecorded(envelope.eventId(), envelope.payload());
            }
            case "payment.transfer.requested" -> {
                var envelope = readEnvelope(body, TransferRequestedPayload.class);
                ingestionService.recordTransferRequested(envelope.eventId(), envelope.payload());
            }
            case "payment.transfer.settled" -> {
                var envelope = readEnvelope(body, TransferSettledPayload.class);
                ingestionService.recordTransferSettled(envelope.eventId(), envelope.payload());
            }
            case "payment.transfer.failed" -> {
                var envelope = readEnvelope(body, TransferFailedPayload.class);
                ingestionService.recordTransferFailed(envelope.eventId(), envelope.payload());
            }
            case "customer.kyc.requested" -> {
                var envelope = readEnvelope(body, KycCheckRequestedPayload.class);
                ingestionService.recordKycCheckRequested(envelope.eventId(), envelope.payload());
            }
            case "customer.kyc.approved", "customer.kyc.rejected" -> {
                var envelope = readEnvelope(body, KycCheckDecidedPayload.class);
                ingestionService.recordKycCheckDecided(envelope.eventId(), envelope.payload());
            }
            case "customer.aml.requested" -> {
                var envelope = readEnvelope(body, AmlScreeningRequestedPayload.class);
                ingestionService.recordAmlScreeningRequested(envelope.eventId(), envelope.payload());
            }
            case "customer.aml.cleared", "customer.aml.flagged" -> {
                var envelope = readEnvelope(body, AmlScreeningCompletedPayload.class);
                ingestionService.recordAmlScreeningCompleted(envelope.eventId(), envelope.payload());
            }
            case "customer.aml.failed" -> {
                var envelope = readEnvelope(body, AmlScreeningFailedPayload.class);
                ingestionService.recordAmlScreeningFailed(envelope.eventId(), envelope.payload());
            }
            case "customer.document.uploaded" -> {
                var envelope = readEnvelope(body, DocumentUploadedPayload.class);
                ingestionService.recordDocumentUploaded(envelope.eventId(), envelope.payload());
            }
            case "customer.document.verified", "customer.document.rejected" -> {
                var envelope = readEnvelope(body, DocumentReviewedPayload.class);
                ingestionService.recordDocumentReviewed(envelope.eventId(), envelope.payload());
            }
            case "investment.subscription.reserved" -> {
                var envelope = readEnvelope(body, SubscriptionReservedPayload.class);
                ingestionService.recordSubscriptionReserved(envelope.eventId(), envelope.payload());
            }
            case "investment.subscription.failed" -> {
                var envelope = readEnvelope(body, SubscriptionFailedPayload.class);
                ingestionService.recordSubscriptionFailed(envelope.eventId(), envelope.payload());
            }
            case "investment.subscription.confirmed" -> {
                var envelope = readEnvelope(body, SubscriptionConfirmedPayload.class);
                ingestionService.recordSubscriptionConfirmed(envelope.eventId(), envelope.payload());
            }
            case "investment.subscription.cancelled" -> {
                var envelope = readEnvelope(body, SubscriptionCancelledPayload.class);
                ingestionService.recordSubscriptionCancelled(envelope.eventId(), envelope.payload());
            }
            case "investment.subscription.timed-out" -> {
                var envelope = readEnvelope(body, SubscriptionTimedOutPayload.class);
                ingestionService.recordSubscriptionTimedOut(envelope.eventId(), envelope.payload());
            }
            default -> log.debug("Ignoring event with routing key {} — not one this service's read-models track",
                    routingKey);
        }
    }

    private <T> EventEnvelope<T> readEnvelope(String body, Class<T> payloadType) throws IOException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        return objectMapper.readValue(body, type);
    }
}
