package com.company.reporting.messaging;

import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.reporting.application.ReportingIngestionService;
import com.company.reporting.messaging.dto.FundNavUpdatedPayload;
import com.company.reporting.messaging.dto.FundRegisteredPayload;
import com.company.reporting.messaging.dto.PortfolioOpenedPayload;
import com.company.reporting.messaging.dto.PositionRecordedPayload;
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
 * routing key. Every other event on the {@code domain-events} exchange
 * (customer.*, kyc.*, aml.*, document.*, investment.*) is bound out at the
 * queue level (see {@link ReportingMessagingConfig}), so the {@code default}
 * branch below is defensive, not load-bearing.
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
            default -> log.debug("Ignoring event with routing key {} — not one this service's read-models track",
                    routingKey);
        }
    }

    private <T> EventEnvelope<T> readEnvelope(String body, Class<T> payloadType) throws IOException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        return objectMapper.readValue(body, type);
    }
}
