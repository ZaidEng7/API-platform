package com.company.platform.web.response;

/** Pagination metadata for collection responses (guide §10.2, §11.1). */
public record PageMeta(int page, int size, long totalElements, int totalPages) {
}
