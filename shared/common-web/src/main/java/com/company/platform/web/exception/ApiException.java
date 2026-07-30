package com.company.platform.web.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by business services for domain-specific error conditions. The
 * shared {@link GlobalExceptionHandler} translates it into a Problem Details
 * response (guide §11.2); errorCode should follow the per-service registry
 * convention (e.g. {@code CUST-4041}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
