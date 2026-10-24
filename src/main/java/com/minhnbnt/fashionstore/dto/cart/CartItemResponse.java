package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record CartItemResponse(
    @Schema(description = "Variant id")
    UUID variantId,

    @Schema(description = "Product id")
    UUID productId,

    @Schema(description = "Shop id")
    UUID shopId,

    @Schema(description = "Ten shop")
    String shopName,

    @Schema(description = "Ten product hien tai")
    String productName,

    @Schema(description = "S3 key anh dai dien, null neu chua co", nullable = true)
    @Nullable
    String imageKey,

    @Schema(description = "Nhan dang variant, vd 'M / Den'")
    String variantLabel,

    @Schema(description = "SKU hien tai")
    String sku,

    @Schema(description = "Gia hien tai VND")
    long unitPrice,

    @Schema(description = "Gia tai thoi diem them vao gio hang VND")
    long priceAtAdded,

    @Schema(description = "Co danh dau gia da thay doi so voi luc them")
    boolean priceChanged,

    int quantity,

    @Schema(description = "Ton hien tai de UI canh bao")
    int stockQuantity
) {
}
