package com.company.fundmgmtadapter.legacy;

import com.company.fundmgmtadapter.legacy.dto.LegacyFundNavRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyFundMgmtTranslatorTest {

    private final LegacyFundMgmtTranslator translator = new LegacyFundMgmtTranslator();

    @Test
    void translatesScaledIntegerNavAndLegacyDate() {
        var legacy = new LegacyFundNavRecord("EQFND01", 105023L, "20260730");

        var canonical = translator.toCanonical(legacy);

        assertThat(canonical.fundCode()).isEqualTo("EQFND01");
        assertThat(canonical.navPerShare()).isEqualByComparingTo(new BigDecimal("10.5023"));
        assertThat(canonical.asOfDate()).isEqualTo(LocalDate.of(2026, 7, 30));
    }

    @Test
    void handlesWholeNumberNav() {
        var legacy = new LegacyFundNavRecord("BONDFND", 1000000L, "20260101");

        var canonical = translator.toCanonical(legacy);

        assertThat(canonical.navPerShare()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }
}
