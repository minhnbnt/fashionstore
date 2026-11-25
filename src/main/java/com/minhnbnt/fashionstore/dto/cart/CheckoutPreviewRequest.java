package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CheckoutPreviewRequest(
    @Schema(
        description = "Chỉ tính các variant này, null/rỗng = toàn bộ giỏ",
        nullable = true
    )
    @Nullable
    List<UUID> variantIds
) {
}
