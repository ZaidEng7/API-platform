package com.company.platform.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox/idempotent-consumer persistence needs a real Postgres instance to
 * test meaningfully (Testcontainers, lands with shared/common-test — see
 * roadmap). This covers what's testable without a DB: the envelope's JSON
 * contract, since that's what actually crosses the wire.
 */
class EventEnvelopeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTripsThroughJson() throws Exception {
        EventEnvelope<Map<String, Object>> original = new EventEnvelope<>(
                UUID.randomUUID(),
                "customer.kyc.approved",
                Instant.parse("2026-07-30T10:00:00Z"),
                "corr-1",
                "customer-service",
                1,
                Map.of("customerId", "abc-123"));

        String json = objectMapper.writeValueAsString(original);
        @SuppressWarnings("unchecked")
        EventEnvelope<Map<String, Object>> roundTripped =
                objectMapper.readValue(json, EventEnvelope.class);

        assertThat(roundTripped.eventId()).isEqualTo(original.eventId());
        assertThat(roundTripped.eventType()).isEqualTo("customer.kyc.approved");
        assertThat(roundTripped.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(roundTripped.payload()).containsEntry("customerId", "abc-123");
    }
}
