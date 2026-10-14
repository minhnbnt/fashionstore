package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CheckoutBlockedItem(
    @Schema(description = "Variant khong checkout duoc")
    UUID variantId,

    @Schema(description = "Ly do tieng Anh, vd 'Sold out'")
    String reason
) {
}
