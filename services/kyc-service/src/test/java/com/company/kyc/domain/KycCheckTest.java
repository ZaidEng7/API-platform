package com.company.kyc.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KycCheckTest {

    @Test
    void newCheckStartsPending() {
        var check = new KycCheck(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(check.getStatus()).isEqualTo(KycCheckStatus.PENDING);
        assertThat(check.getReason()).isNull();
        assertThat(check.getDecidedBy()).isNull();
    }

    @Test
    void decideApprovedSetsStatusReasonAndDecider() {
        var check = new KycCheck(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        check.decide(KycCheckStatus.APPROVED, "Documents verified", "compliance-officer-1");

        assertThat(check.getStatus()).isEqualTo(KycCheckStatus.APPROVED);
        assertThat(check.getReason()).isEqualTo("Documents verified");
        assertThat(check.getDecidedBy()).isEqualTo("compliance-officer-1");
    }

    @Test
    void decidingAnAlreadyDecidedCheckThrows() {
        var check = new KycCheck(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        check.decide(KycCheckStatus.REJECTED, "Sanctions list match", "compliance-officer-1");

        assertThatThrownBy(() -> check.decide(KycCheckStatus.APPROVED, "changed my mind", "compliance-officer-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");
    }
}
