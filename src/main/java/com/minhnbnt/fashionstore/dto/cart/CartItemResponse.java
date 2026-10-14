package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CartItemResponse(
    @Schema(description = "Variant id")
    UUID variantId,

    @Schema(description = "Ten product hien tai")
    String productName,

    @Schema(description = "Nhan dang variant, vd 'M / Den'")
    String variantLabel,

    @Schema(description = "SKU hien tai")
    String sku,

    @Schema(description = "Gia hien tai VND")
    long unitPrice,

    int quantity,

    @Schema(description = "Ton hien tai de UI canh bao")
    int stockQuantity
) {
}
