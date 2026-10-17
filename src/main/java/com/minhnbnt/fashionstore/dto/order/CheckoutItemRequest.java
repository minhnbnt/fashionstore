package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutItemRequest(
    @Schema(description = "Variant id muon mua")
    @NotNull
    UUID variantId,

    @Schema(example = "2")
    @Min(1)
    int quantity
) {
}
