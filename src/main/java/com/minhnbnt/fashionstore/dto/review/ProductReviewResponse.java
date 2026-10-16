package com.minhnbnt.fashionstore.dto.review;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductReviewResponse(
    UUID id,
    UUID orderId,
    UUID productId,
    UUID shopId,
    UUID buyerId,
    short rating,
    @Nullable String comment,
    List<String> imageKeys,
    @Nullable String sellerReply,
    @Nullable Instant repliedAt,
    boolean isReported,
    Instant createdAt
) {
}
