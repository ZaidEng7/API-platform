package com.company.crmadapter.api.dto;

/**
 * Canonical, business-language shape — what every consumer of this adapter
 * actually sees. The legacy system's field names, status codes, and Y/N
 * flags stop at {@link com.company.crmadapter.legacy.LegacyCrmClient};
 * nothing legacy-shaped crosses this boundary (guide §9.1).
 */
public record CrmCustomerResponse(String id, String fullName, String email, CustomerStatus status, boolean vip) {

    public enum CustomerStatus {
        ACTIVE, INACTIVE
    }
}
