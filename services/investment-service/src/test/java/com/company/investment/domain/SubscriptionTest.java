package com.company.investment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    @Test
    void reservedStartsAwaitingPayment() {
        var subscription = reservedSubscription();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.AWAITING_PAYMENT);
        assertThat(subscription.getFailureReason()).isNull();
        assertThat(subscription.getConfirmedAt()).isNull();
    }

    @Test
    void failedStartsInATerminalStateWithAReason() {
        var subscription = Subscription.failed(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "EQFND01", BigDecimal.TEN, "AML screening not clear",
                Instant.now());

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.FAILED);
        assertThat(subscription.getFailureReason()).isEqualTo("AML screening not clear");
        assertThatThrownBy(subscription::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmTransitionsToConfirmedAndSetsConfirmedAt() {
        var subscription = reservedSubscription();

        subscription.confirm();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CONFIRMED);
        assertThat(subscription.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirmingTwiceThrows() {
        var subscription = reservedSubscription();
        subscription.confirm();

        assertThatThrownBy(subscription::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not awaiting payment");
    }

    @Test
    void cancelTransitionsToCancelled() {
        var subscription = reservedSubscription();

        subscription.cancel();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void cancellingAConfirmedSubscriptionThrows() {
        var subscription = reservedSubscription();
        subscription.confirm();

        assertThatThrownBy(subscription::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeoutTransitionsToTimedOut() {
        var subscription = reservedSubscription();

        subscription.timeout();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TIMED_OUT);
    }

    private Subscription reservedSubscription() {
        Instant now = Instant.now();
        return Subscription.reserved(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "EQFND01", BigDecimal.TEN, now, now.plusSeconds(900));
    }
}
