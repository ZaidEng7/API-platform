package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTransferViewTest {

    @Test
    void newTransferViewStartsPending() {
        var view = new PaymentTransferView(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "USD", Instant.now());

        assertThat(view.getStatus()).isEqualTo(TransferReportStatus.PENDING);
        assertThat(view.getSettledAt()).isNull();
        assertThat(view.getFailedAt()).isNull();
    }

    @Test
    void settleTransitionsToSettled() {
        var view = new PaymentTransferView(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "USD", Instant.now());
        Instant settledAt = Instant.now();

        view.settle(settledAt);

        assertThat(view.getStatus()).isEqualTo(TransferReportStatus.SETTLED);
        assertThat(view.getSettledAt()).isEqualTo(settledAt);
    }

    @Test
    void failTransitionsToFailedWithAReason() {
        var view = new PaymentTransferView(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "USD", Instant.now());

        view.fail("Card declined by issuer", Instant.now());

        assertThat(view.getStatus()).isEqualTo(TransferReportStatus.FAILED);
        assertThat(view.getFailureReason()).isEqualTo("Card declined by issuer");
    }
}
