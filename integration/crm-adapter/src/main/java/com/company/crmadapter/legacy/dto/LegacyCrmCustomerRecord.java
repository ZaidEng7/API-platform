package com.company.crmadapter.legacy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TEMPLATE — the legacy CRM's actual response shape (SCREAMING_SNAKE_CASE
 * field names, single-char status codes, Y/N flags instead of booleans).
 * This record is deliberately fictional; a real integration replaces it
 * with whatever the real legacy system actually returns. It exists only so
 * {@link com.company.crmadapter.legacy.LegacyCrmClient} has something
 * concrete to translate — see ../../../../../../../README.md.
 */
public record LegacyCrmCustomerRecord(
        @JsonProperty("CUST_ID") String custId,
        @JsonProperty("CUST_NM") String custNm,
        @JsonProperty("EMAIL_ADDR") String emailAddr,
        @JsonProperty("CUST_STATUS_CD") String custStatusCd,
        @JsonProperty("VIP_FLG") String vipFlg) {
}
