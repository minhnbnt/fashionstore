package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CheckoutPreviewRequest(
    @Schema(
        description = "Chỉ tính các variant này, null/rỗng = toàn bộ giỏ",
        nullable = true
    )
    @Nullable
    List<UUID> variantIds,

    @Schema(
        description = "Mã giảm giá áp dụng theo từng shop (shopId -> voucherCode)",
        nullable = true
    )
    @Nullable
    Map<UUID, String> shopVouchers
) {

    public CheckoutPreviewRequest(@Nullable List<UUID> variantIds) {
        this(variantIds, null);
    }
}
