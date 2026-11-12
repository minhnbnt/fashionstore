package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record OrderItemResponse(
    UUID id,

    @Schema(nullable = true)
    @Nullable
    UUID productId,

    @Schema(nullable = true)
    @Nullable
    UUID variantId,

    String productName,

    String variantLabel,

    String sku,

    long unitPrice,

    int quantity,

    long lineTotal,

    @Schema(description = "Storage key ảnh đại diện sản phẩm/biến thể", nullable = true)
    @Nullable
    String imageKey,

    @Schema(description = "Sản phẩm còn đang mở bán hay đã ẩn/ngừng bán")
    boolean productAvailable
) {
}
