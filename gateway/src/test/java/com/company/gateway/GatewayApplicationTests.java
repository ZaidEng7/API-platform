package com.company.gateway;

import org.junit.jupiter.api.Test;

class GatewayApplicationTests {

    @Test
    void applicationClassIsLoadable() {
        // Full context-load + route integration tests land once shared/common-test exists.
        new GatewayApplication();
    }
}
