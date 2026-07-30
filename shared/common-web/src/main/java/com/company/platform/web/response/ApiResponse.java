package com.company.platform.web.response;

/**
 * Standard success envelope (guide §11.1). {@code meta} is present only for
 * collection responses.
 */
public record ApiResponse<T>(boolean success, T data, PageMeta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta);
    }
}
