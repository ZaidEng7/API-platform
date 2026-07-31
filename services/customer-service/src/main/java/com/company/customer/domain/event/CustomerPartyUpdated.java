package com.company.customer.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Payload for {@code customer.party.updated} (guide §22 naming). */
public record CustomerPartyUpdated(UUID customerId, String fullName, String phone, LocalDate dateOfBirth,
                                    Instant updatedAt) {
}
