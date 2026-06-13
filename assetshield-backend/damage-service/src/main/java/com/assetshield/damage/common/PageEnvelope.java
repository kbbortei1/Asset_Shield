package com.assetshield.damage.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Pagination shape used by every list endpoint. */
public record PageEnvelope<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static final int MAX_SIZE = 100;

    public static <T> PageEnvelope<T> of(Page<T> page) {
        return new PageEnvelope<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Clamps ?page=&size= query params: page >= 0, 1 <= size <= 100. */
    public static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    public static int clampPage(int page) {
        return Math.max(0, page);
    }
}
