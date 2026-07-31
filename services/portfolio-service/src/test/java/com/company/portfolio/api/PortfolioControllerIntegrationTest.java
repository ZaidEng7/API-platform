package com.company.portfolio.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20). Ownership
 * (guide §12.2) tests use {@code jwt()}, not {@code @WithMockUser} —
 * {@code @WithMockUser} produces a {@code UsernamePasswordAuthenticationToken}
 * whose username {@link com.company.platform.security.CurrentUser#subject()}
 * can't read (it only reads a real {@code JwtAuthenticationToken}'s
 * {@code sub} claim), so it can't exercise the ownership-matching logic at
 * all — only {@code jwt()} lets a test control the caller's asserted
 * identity precisely enough to prove both the "matches" and
 * "doesn't match" branches.
 */
class PortfolioControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openingAPortfolioRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequestBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void opensAndFetchesAPortfolioAsStaff() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        String location = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequestBody(customerId, ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(user("portfolio-manager").roles("PORTFOLIO_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()));
    }

    @Test
    void investorCanViewTheirOwnPortfolio() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String location = openPortfolio(UUID.randomUUID(), ownerId);

        mockMvc.perform(get(location).with(jwt()
                        .jwt(jwtBuilder -> jwtBuilder.subject(ownerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()));
    }

    @Test
    void investorCannotViewAnotherInvestorsPortfolio() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        String location = openPortfolio(UUID.randomUUID(), ownerId);

        mockMvc.perform(get(location).with(jwt()
                        .jwt(jwtBuilder -> jwtBuilder.subject(someoneElse.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PORTFOLIO-4030"));
    }

    @Test
    void investorCanListTheirOwnPortfoliosButNotSomeoneElses() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        openPortfolio(UUID.randomUUID(), ownerId);

        mockMvc.perform(get("/api/v1/portfolios").param("ownerId", ownerId.toString())
                        .with(jwt().jwt(jwtBuilder -> jwtBuilder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/portfolios").param("ownerId", ownerId.toString())
                        .with(jwt().jwt(jwtBuilder -> jwtBuilder.subject(someoneElse.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PORTFOLIO-4030"));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void staffCanViewAnyPortfolioRegardlessOfOwner() throws Exception {
        String location = openPortfolio(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(get(location)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void returnsProblemDetailsForUnknownPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PORTFOLIO-4041"));
    }

    @Test
    void recordingAPositionRequiresAStaffRoleEvenForThePortfolioOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String location = openPortfolio(UUID.randomUUID(), ownerId);

        mockMvc.perform(post(location + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND01", "quantity": 100}
                                """)
                        .with(jwt().jwt(jwtBuilder -> jwtBuilder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void recordsAndListsPositions() throws Exception {
        String location = openPortfolio(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(location + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND01", "quantity": 100.5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fundCode").value("EQFND01"))
                .andExpect(jsonPath("$.data.quantity").value(100.5));

        mockMvc.perform(get(location + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private String openPortfolio(UUID customerId, UUID ownerId) throws Exception {
        return mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequestBody(customerId, ownerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private String openRequestBody(UUID customerId, UUID ownerId) {
        return """
                {"customerId": "%s", "ownerId": "%s", "name": "Retirement Account", "currency": "USD"}
                """.formatted(customerId, ownerId);
    }
}
