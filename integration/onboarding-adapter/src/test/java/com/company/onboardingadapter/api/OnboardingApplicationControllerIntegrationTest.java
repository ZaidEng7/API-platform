package com.company.onboardingadapter.api;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP round-trip through the whole adapter (controller -> resilience
 * layer -> translation, both directions), against a stub legacy backend —
 * proves the anti-corruption boundary holds for a write too: neither the
 * legacy request shape nor its response shape leaks through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OnboardingApplicationControllerIntegrationTest {

    // Fixed, not dynamic — see LegacyOnboardingClientResilienceTest for why.
    private static final int WIREMOCK_PORT = 9995;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyOnboardingBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-onboarding.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitsAndTranslatesBothDirections() throws Exception {
        stubFor(post(urlPathMatching("/onboarding/v1/applications"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"APPL_REF_NO": "REF-42", "APPL_STATUS_CD": "P"}
                                """)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/onboarding-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ada Lovelace", "email": "ada@example.com", "dateOfBirth": "1990-03-05"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.applicationId").value("REF-42"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsInvalidRequestBeforeEverCallingTheLegacySystem() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/onboarding-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "", "email": "not-an-email", "dateOfBirth": "1990-03-05"}
                                """))
                .andExpect(status().isBadRequest());

        wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/onboarding/v1/applications")));
    }
}
