package com.minhnbnt.fashionstore.dto.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WishlistToggleRequest(
    @Schema(description = "Product id muon luu/bo luu")
    @NotNull
    UUID productId
) {
}
