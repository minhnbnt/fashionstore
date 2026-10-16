package com.minhnbnt.fashionstore.dto.shop;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record DecorationResponse(
    UUID id,
    UUID shopId,
    String layout,
    @Nullable String draft,
    @Nullable Instant publishedAt,
    Instant updatedAt
) {
}
