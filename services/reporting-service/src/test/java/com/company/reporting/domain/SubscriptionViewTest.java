package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionViewTest {

    @Test
    void newSubscriptionViewFromReservationStartsAwaitingPayment() {
        var view = new SubscriptionView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EQFND01",
                BigDecimal.TEN, Instant.now());

        assertThat(view.getStatus()).isEqualTo(SubscriptionReportStatus.AWAITING_PAYMENT);
        assertThat(view.getConfirmedAt()).isNull();
    }

    @Test
    void newSubscriptionViewFromImmediateFailureStartsFailed() {
        var view = new SubscriptionView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(view.getStatus()).isEqualTo(SubscriptionReportStatus.FAILED);
    }

    @Test
    void confirmTransitionsToConfirmed() {
        var view = new SubscriptionView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EQFND01",
                BigDecimal.TEN, Instant.now());
        Instant confirmedAt = Instant.now();

        view.confirm(confirmedAt);

        assertThat(view.getStatus()).isEqualTo(SubscriptionReportStatus.CONFIRMED);
        assertThat(view.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void cancelTransitionsToCancelled() {
        var view = new SubscriptionView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EQFND01",
                BigDecimal.TEN, Instant.now());
        Instant cancelledAt = Instant.now();

        view.cancel(cancelledAt);

        assertThat(view.getStatus()).isEqualTo(SubscriptionReportStatus.CANCELLED);
        assertThat(view.getCancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    void timeoutTransitionsToTimedOut() {
        var view = new SubscriptionView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EQFND01",
                BigDecimal.TEN, Instant.now());
        Instant timedOutAt = Instant.now();

        view.timeout(timedOutAt);

        assertThat(view.getStatus()).isEqualTo(SubscriptionReportStatus.TIMED_OUT);
        assertThat(view.getTimedOutAt()).isEqualTo(timedOutAt);
    }
}
