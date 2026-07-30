package com.company.platform.web.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test: Spring MVC's own @ExceptionHandler resolution runs
 * before Spring Security's ExceptionTranslationFilter gets a chance, so a
 * catch-all {@code @ExceptionHandler(Exception.class)} — like the one
 * below for truly unexpected errors — would otherwise swallow
 * AccessDeniedException and turn a @PreAuthorize denial into a misleading
 * 500 instead of 403. Caught while building Audit Service, whose read API
 * is @PreAuthorize-restricted.
 */
@SpringBootTest(classes = {
        GlobalExceptionHandlerTest.TestApp.class,
        GlobalExceptionHandlerTest.ThrowingController.class
})
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accessDeniedExceptionMapsTo403WithProblemDetails() throws Exception {
        mockMvc.perform(get("/throw-access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void unexpectedExceptionStillMapsTo500() throws Exception {
        mockMvc.perform(get("/throw-runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @SpringBootApplication
    static class TestApp {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/throw-access-denied")
        String throwAccessDenied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/throw-runtime")
        String throwRuntime() {
            throw new RuntimeException("boom");
        }
    }
}
