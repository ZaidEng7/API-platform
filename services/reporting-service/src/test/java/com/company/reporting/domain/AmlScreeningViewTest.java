package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmlScreeningViewTest {

    @Test
    void newScreeningViewStartsInProgress() {
        var view = new AmlScreeningView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(view.getStatus()).isEqualTo(AmlScreeningReportStatus.IN_PROGRESS);
        assertThat(view.getCompletedAt()).isNull();
        assertThat(view.getFailedAt()).isNull();
    }

    @Test
    void completeTransitionsToCompletedWithAnOutcome() {
        var view = new AmlScreeningView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        Instant completedAt = Instant.now();

        view.complete(AmlScreeningReportOutcome.HIT, "Sanctions list match", completedAt);

        assertThat(view.getStatus()).isEqualTo(AmlScreeningReportStatus.COMPLETED);
        assertThat(view.getOutcome()).isEqualTo(AmlScreeningReportOutcome.HIT);
        assertThat(view.getNotes()).isEqualTo("Sanctions list match");
        assertThat(view.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void failTransitionsToFailedWithAReason() {
        var view = new AmlScreeningView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        view.fail("Screening provider timed out", Instant.now());

        assertThat(view.getStatus()).isEqualTo(AmlScreeningReportStatus.FAILED);
        assertThat(view.getFailureReason()).isEqualTo("Screening provider timed out");
    }
}
