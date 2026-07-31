package com.company.platform.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for a real incident: adding spring-boot-starter-security
 * to the classpath (which this module does) makes Spring Boot
 * auto-configure a default SecurityFilterChain that requires auth
 * everywhere with a generated password, UNLESS something else registers
 * its own chain first — which is exactly what happened when this same bug
 * was found in the Gateway. Without issuer-uri configured, a service that
 * merely depends on common-security must stay fully open, not get
 * silently locked down.
 */
@SpringBootTest(classes = {
        CommonSecurityAutoConfigurationTest.TestApp.class,
        CommonSecurityAutoConfigurationTest.ProbeController.class
})
@AutoConfigureMockMvc
class CommonSecurityAutoConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void staysOpenWhenNoIssuerUriIsConfigured() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isOk());
    }

    @SpringBootApplication
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() {
            return "ok";
        }
    }
}
