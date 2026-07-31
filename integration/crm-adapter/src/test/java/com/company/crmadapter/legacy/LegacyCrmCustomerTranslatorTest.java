package com.company.crmadapter.legacy;

import com.company.crmadapter.api.dto.CrmCustomerResponse;
import com.company.crmadapter.legacy.dto.LegacyCrmCustomerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyCrmCustomerTranslatorTest {

    private final LegacyCrmCustomerTranslator translator = new LegacyCrmCustomerTranslator();

    @Test
    void translatesActiveVipCustomer() {
        var legacy = new LegacyCrmCustomerRecord("12345", "Ada Lovelace", "ada@example.com", "A", "Y");

        CrmCustomerResponse canonical = translator.toCanonical(legacy);

        assertThat(canonical.id()).isEqualTo("12345");
        assertThat(canonical.fullName()).isEqualTo("Ada Lovelace");
        assertThat(canonical.email()).isEqualTo("ada@example.com");
        assertThat(canonical.status()).isEqualTo(CrmCustomerResponse.CustomerStatus.ACTIVE);
        assertThat(canonical.vip()).isTrue();
    }

    @Test
    void translatesInactiveNonVipCustomer() {
        var legacy = new LegacyCrmCustomerRecord("999", "Grace Hopper", "grace@example.com", "I", "N");

        CrmCustomerResponse canonical = translator.toCanonical(legacy);

        assertThat(canonical.status()).isEqualTo(CrmCustomerResponse.CustomerStatus.INACTIVE);
        assertThat(canonical.vip()).isFalse();
    }

    @Test
    void rejectsUnknownStatusCode() {
        var legacy = new LegacyCrmCustomerRecord("1", "Nobody", "n@example.com", "X", "N");

        assertThatThrownBy(() -> translator.toCanonical(legacy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }
}
