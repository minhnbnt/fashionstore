package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CartResponse(
    @Schema(description = "Item xep theo thu tu them vao")
    List<CartItemResponse> items,

    @Schema(description = "Tong so luong")
    int itemCount,

    @Schema(description = "Tam tinh VND theo gia hien tai")
    long subtotal
) {
}
