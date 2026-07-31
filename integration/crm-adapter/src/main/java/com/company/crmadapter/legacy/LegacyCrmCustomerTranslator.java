package com.company.crmadapter.legacy;

import com.company.crmadapter.api.dto.CrmCustomerResponse;
import com.company.crmadapter.legacy.dto.LegacyCrmCustomerRecord;
import org.springframework.stereotype.Component;

/**
 * The translation half of the anti-corruption layer: legacy status codes
 * ("A"/"I") and Y/N flags become a real enum and a real boolean. This is
 * exactly the kind of legacy quirk §9.1 says should never reach a consumer.
 */
@Component
public class LegacyCrmCustomerTranslator {

    public CrmCustomerResponse toCanonical(LegacyCrmCustomerRecord legacy) {
        return new CrmCustomerResponse(
                legacy.custId(),
                legacy.custNm(),
                legacy.emailAddr(),
                translateStatus(legacy.custStatusCd()),
                "Y".equalsIgnoreCase(legacy.vipFlg()));
    }

    private CrmCustomerResponse.CustomerStatus translateStatus(String custStatusCd) {
        return switch (custStatusCd) {
            case "A" -> CrmCustomerResponse.CustomerStatus.ACTIVE;
            case "I" -> CrmCustomerResponse.CustomerStatus.INACTIVE;
            default -> throw new IllegalStateException("Unknown legacy CUST_STATUS_CD: " + custStatusCd);
        };
    }
}
