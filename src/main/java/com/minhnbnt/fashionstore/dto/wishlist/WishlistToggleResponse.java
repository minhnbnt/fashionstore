package com.minhnbnt.fashionstore.dto.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record WishlistToggleResponse(
    @Schema UUID productId,
    @Schema(description = "true = vua luu, false = vua bo luu") boolean saved
) {
}
