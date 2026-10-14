package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartItemRequest(
    @Schema(description = "Variant id muon them vao gio")
    @NotNull
    UUID variantId,

    @Schema(example = "2")
    @Min(1)
    int quantity
) {
}
