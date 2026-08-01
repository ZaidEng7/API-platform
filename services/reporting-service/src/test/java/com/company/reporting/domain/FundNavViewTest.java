package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FundNavViewTest {

    @Test
    void applyRegistrationSetsNameAndCurrencyButLeavesNavAlone() {
        var view = new FundNavView("EQFND01", Instant.now());

        view.applyRegistration("Global Equity Fund", "USD", Instant.now());

        assertThat(view.getName()).isEqualTo("Global Equity Fund");
        assertThat(view.getCurrency()).isEqualTo("USD");
        assertThat(view.getNavPerShare()).isNull();
    }

    @Test
    void applyNavUpdateSetsNavButLeavesNameAlone() {
        var view = new FundNavView("EQFND01", Instant.now());
        view.applyRegistration("Global Equity Fund", "USD", Instant.now());

        view.applyNavUpdate(new BigDecimal("12.3456"), LocalDate.of(2026, 8, 1), Instant.now());

        assertThat(view.getNavPerShare()).isEqualByComparingTo("12.3456");
        assertThat(view.getAsOfDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(view.getName()).isEqualTo("Global Equity Fund");
    }
}
