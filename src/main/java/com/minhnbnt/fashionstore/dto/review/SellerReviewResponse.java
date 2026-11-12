package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SellerReviewResponse(
    UUID id,
    UUID productId,
    String buyerDisplayName,
    int stars,
    @Nullable String comment,
    @Nullable String sellerReply,
    @Nullable Instant repliedAt,
    boolean isReported,
    @Schema boolean edited,
    @Schema(nullable = true) @Nullable Instant editedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
