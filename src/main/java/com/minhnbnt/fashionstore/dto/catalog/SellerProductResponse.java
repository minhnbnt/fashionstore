package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerProductResponse(
    UUID id,
    UUID shopId,
    @Nullable UUID categoryId,
    String name,
    String slug,
    String description,
    @Schema(description = "ACTIVE | HIDDEN | DELETED | BANNED", example = "ACTIVE")
    String status,
    @Schema(
        description = "Loại vi phạm khi bị admin gỡ (status = BANNED), null nếu không bị gỡ",
        nullable = true,
        example = "COUNTERFEIT"
    )
    @Nullable String violationType,
    @Schema(description = "Lý do admin gỡ, null nếu không bị gỡ", nullable = true)
    @Nullable String statusReason,
    int totalStock,
    long minPrice,
    long maxPrice,
    @Nullable String defaultImageKey,
    List<VariantResponse> variants,
    Instant createdAt,
    Instant updatedAt,
    @Schema(description = "Số lượng sản phẩm đã bán thành công", example = "15")
    int soldCount
) {
}
