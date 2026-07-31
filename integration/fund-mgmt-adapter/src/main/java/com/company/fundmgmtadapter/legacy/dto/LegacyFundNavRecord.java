package com.company.fundmgmtadapter.legacy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TEMPLATE — the legacy fund management system's actual response shape:
 * NAV as a scaled integer (x10000, avoiding floating point — a genuinely
 * common convention in real legacy financial systems, not just a
 * fabricated quirk) and a bare "yyyyMMdd" date string. Deliberately
 * fictional — see ../../../../../../../README.md.
 */
public record LegacyFundNavRecord(
        @JsonProperty("FUND_CD") String fundCd,
        @JsonProperty("NAV_VALUE_X10000") long navValueX10000,
        @JsonProperty("NAV_DT_YYYYMMDD") String navDtYyyymmdd) {
}
