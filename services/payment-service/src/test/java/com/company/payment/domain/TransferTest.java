package com.company.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    @Test
    void newTransferStartsPending() {
        var transfer = pendingTransfer();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getFailureReason()).isNull();
        assertThat(transfer.getSettledAt()).isNull();
    }

    @Test
    void settleTransitionsToSettledAndSetsSettledAt() {
        var transfer = pendingTransfer();

        transfer.settle();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SETTLED);
        assertThat(transfer.getSettledAt()).isNotNull();
    }

    @Test
    void settlingTwiceThrows() {
        var transfer = pendingTransfer();
        transfer.settle();

        assertThatThrownBy(transfer::settle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void failTransitionsToFailedWithAReason() {
        var transfer = pendingTransfer();

        transfer.fail("Card declined by issuer");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Card declined by issuer");
    }

    @Test
    void failingAnAlreadySettledTransferThrows() {
        var transfer = pendingTransfer();
        transfer.settle();

        assertThatThrownBy(() -> transfer.fail("too late")).isInstanceOf(IllegalStateException.class);
    }

    private Transfer pendingTransfer() {
        return new Transfer(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, "USD", "tok_visa_1234", "subscription-ref", Instant.now());
    }
}
