package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminProductResponse(
    UUID id,

    UUID shopId,

    String shopName,

    String shopSlug,

    @Schema(description = "Trạng thái shop", example = "APPROVED")
    String shopStatus,

    @Schema(nullable = true)
    @Nullable
    UUID categoryId,

    @Schema(nullable = true, example = "Áo thun")
    @Nullable
    String categoryName,

    String name,

    String slug,

    @Schema(description = "ACTIVE | HIDDEN | DELETED | BANNED", example = "BANNED")
    String status,

    @Schema(
        description = "Loại vi phạm của lần gỡ gần nhất, null nếu không bị gỡ",
        nullable = true,
        example = "COUNTERFEIT"
    )
    @Nullable
    String violationType,

    @Schema(description = "Lý do gỡ gần nhất, null nếu không bị gỡ", nullable = true)
    @Nullable
    String statusReason,

    @Schema(description = "Giá thấp nhất trong các biến thể (VND)", example = "199000")
    long minPrice,

    @Schema(description = "Giá cao nhất trong các biến thể (VND)", example = "259000")
    long maxPrice,

    @Schema(description = "Tổng tồn kho mọi biến thể")
    long totalStock,

    @Schema(description = "Số biến thể")
    long variantCount,

    @Schema(description = "S3 key ảnh đầu tiên, frontend tự ghép base URL", nullable = true)
    @Nullable
    String imageKey,

    Instant createdAt,

    Instant updatedAt
) {
}
