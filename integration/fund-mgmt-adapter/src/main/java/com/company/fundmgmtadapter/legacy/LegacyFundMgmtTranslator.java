package com.company.fundmgmtadapter.legacy;

import com.company.fundmgmtadapter.api.dto.FundNavResponse;
import com.company.fundmgmtadapter.legacy.dto.LegacyFundNavRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The anti-corruption boundary (guide §9.1): the legacy scaled-integer NAV
 * representation and bare date string never reach the canonical API.
 */
@Component
public class LegacyFundMgmtTranslator {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int LEGACY_NAV_SCALE = 4; // x10000

    public FundNavResponse toCanonical(LegacyFundNavRecord legacy) {
        BigDecimal navPerShare = BigDecimal.valueOf(legacy.navValueX10000()).movePointLeft(LEGACY_NAV_SCALE);
        LocalDate asOfDate = LocalDate.parse(legacy.navDtYyyymmdd(), LEGACY_DATE_FORMAT);
        return new FundNavResponse(legacy.fundCd(), navPerShare, asOfDate);
    }
}
