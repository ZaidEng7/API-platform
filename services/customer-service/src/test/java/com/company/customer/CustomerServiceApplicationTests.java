package com.company.customer;

import org.junit.jupiter.api.Test;

class CustomerServiceApplicationTests {

    @Test
    void applicationClassIsLoadable() {
        // Full context-load integration test (Testcontainers PostgreSQL) lands with
        // shared/common-test in the next iteration — this placeholder keeps CI green
        // until that shared module exists.
        new CustomerServiceApplication();
    }
}
