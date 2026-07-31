package com.company.aml.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmlScreeningTest {

    @Test
    void newScreeningStartsInProgress() {
        var screening = new AmlScreening(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(screening.getStatus()).isEqualTo(ScreeningStatus.IN_PROGRESS);
        assertThat(screening.getOutcome()).isNull();
        assertThat(screening.getCompletedAt()).isNull();
    }

    @Test
    void completeSetsStatusOutcomeAndCompletedAt() {
        var screening = new AmlScreening(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        screening.complete(ScreeningOutcome.CLEAR, "No watchlist match");

        assertThat(screening.getStatus()).isEqualTo(ScreeningStatus.COMPLETED);
        assertThat(screening.getOutcome()).isEqualTo(ScreeningOutcome.CLEAR);
        assertThat(screening.getNotes()).isEqualTo("No watchlist match");
        assertThat(screening.getCompletedAt()).isNotNull();
    }

    @Test
    void failSetsStatusAndReasonButNoOutcome() {
        var screening = new AmlScreening(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        screening.fail("Watchlist vendor adapter unavailable");

        assertThat(screening.getStatus()).isEqualTo(ScreeningStatus.FAILED);
        assertThat(screening.getNotes()).isEqualTo("Watchlist vendor adapter unavailable");
        assertThat(screening.getOutcome()).isNull();
        assertThat(screening.getCompletedAt()).isNull();
    }

    @Test
    void completingAScreeningThatIsNoLongerInProgressThrows() {
        var screening = new AmlScreening(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        screening.complete(ScreeningOutcome.CLEAR, "No watchlist match");

        assertThatThrownBy(() -> screening.complete(ScreeningOutcome.HIT, "changed my mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer in progress");
    }

    @Test
    void failingAScreeningThatIsNoLongerInProgressThrows() {
        var screening = new AmlScreening(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        screening.fail("Watchlist vendor adapter unavailable");

        assertThatThrownBy(() -> screening.fail("retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer in progress");
    }
}
