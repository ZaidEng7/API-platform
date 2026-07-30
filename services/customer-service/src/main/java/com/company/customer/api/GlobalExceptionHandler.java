package com.company.customer.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * RFC 7807 Problem Details error contract per guide §11.2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return withCommonFields(problem, request, "CUST-4041");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request contains " + ex.getBindingResult().getErrorCount() + " invalid field(s)");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .toList());
        return withCommonFields(problem, request, "CUST-4001");
    }

    private ProblemDetail withCommonFields(ProblemDetail problem, HttpServletRequest request, String errorCode) {
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get("correlationId"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errorCode", errorCode);
        return problem;
    }

    private record FieldError(String field, String code, String message) {
    }
}
