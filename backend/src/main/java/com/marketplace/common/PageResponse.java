package com.marketplace.common;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResponse<>(content, page, size, totalElements,
                (int) ((totalElements + size - 1) / size));
    }
}
