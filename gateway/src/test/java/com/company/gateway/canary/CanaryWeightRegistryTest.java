package com.company.gateway.canary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanaryWeightRegistryTest {

    @Test
    void defaultsToZeroForAnUnknownMigration() {
        var registry = new CanaryWeightRegistry();

        assertThat(registry.getLegacyWeightPercent("unknown")).isZero();
    }

    @Test
    void storesAndReturnsAnUpdatedWeight() {
        var registry = new CanaryWeightRegistry();

        registry.setLegacyWeightPercent("customer-lookup", 25);

        assertThat(registry.getLegacyWeightPercent("customer-lookup")).isEqualTo(25);
    }

    @Test
    void rejectsAnOutOfRangeWeight() {
        var registry = new CanaryWeightRegistry();

        assertThatThrownBy(() -> registry.setLegacyWeightPercent("customer-lookup", 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.setLegacyWeightPercent("customer-lookup", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
