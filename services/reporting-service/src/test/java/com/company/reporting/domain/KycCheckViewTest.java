package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KycCheckViewTest {

    @Test
    void newCheckViewStartsPending() {
        var view = new KycCheckView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(view.getStatus()).isEqualTo(KycCheckReportStatus.PENDING);
        assertThat(view.getDecidedAt()).isNull();
    }

    @Test
    void decideTransitionsToApproved() {
        var view = new KycCheckView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        Instant decidedAt = Instant.now();

        view.decide(KycCheckReportStatus.APPROVED, null, "compliance-officer-1", decidedAt);

        assertThat(view.getStatus()).isEqualTo(KycCheckReportStatus.APPROVED);
        assertThat(view.getDecidedBy()).isEqualTo("compliance-officer-1");
        assertThat(view.getDecidedAt()).isEqualTo(decidedAt);
    }

    @Test
    void decideTransitionsToRejectedWithAReason() {
        var view = new KycCheckView(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        view.decide(KycCheckReportStatus.REJECTED, "Document mismatch", "compliance-officer-2", Instant.now());

        assertThat(view.getStatus()).isEqualTo(KycCheckReportStatus.REJECTED);
        assertThat(view.getReason()).isEqualTo("Document mismatch");
    }
}
