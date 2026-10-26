package com.minhnbnt.fashionstore.dto.catalog;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record WishlistProductCard(
    UUID productId,
    @Nullable String name,
    @Nullable String slug,
    @Nullable Long minPrice,
    @Nullable String defaultImageKey,
    boolean available,
    @Nullable String unavailableReason
) {
}
