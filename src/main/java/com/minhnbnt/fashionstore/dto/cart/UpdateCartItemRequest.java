package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Schema(example = "3")
    @Min(1)
    int quantity
) {
}
