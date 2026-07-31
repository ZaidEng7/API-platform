package com.company.platform.messaging.autoconfigure;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 4 no longer auto-configures a com.fasterxml.jackson ObjectMapper
 * bean by default (its own Jackson autoconfiguration defaults to Jackson 3) —
 * this covers the fallback bean OutboxEventStore relies on, including that
 * JavaTimeModule actually got registered (Instant serialization would throw
 * otherwise).
 */
class CommonMessagingAutoConfigurationTest {

    @Test
    void objectMapperSerializesInstants() throws Exception {
        var objectMapper = new CommonMessagingAutoConfiguration().objectMapper();

        String json = objectMapper.writeValueAsString(Instant.parse("2026-07-30T10:00:00Z"));

        assertThat(json).isEqualTo("\"2026-07-30T10:00:00Z\"");
    }
}
