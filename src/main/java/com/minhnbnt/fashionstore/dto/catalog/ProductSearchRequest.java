package com.minhnbnt.fashionstore.dto.catalog;

import org.jspecify.annotations.Nullable;

/**
 * Tham số search gom từ query string. Field null = không lọc theo field đó.
 */
public record ProductSearchRequest(
    @Nullable String q,
    @Nullable String category,
    @Nullable Long minPrice,
    @Nullable Long maxPrice,
    @Nullable String size,
    @Nullable String color,
    @Nullable ProductSort sort,
    int page,
    int limit
) {
}
