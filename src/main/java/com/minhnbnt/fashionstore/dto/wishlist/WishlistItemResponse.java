package com.minhnbnt.fashionstore.dto.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record WishlistItemResponse(
    @Schema UUID productId,
    @Schema(nullable = true) @Nullable String name,
    @Schema(nullable = true) @Nullable String slug,
    @Schema(description = "Gia thap nhat hien tai, null khi khong con ban", nullable = true)
    @Nullable Long minPrice,
    @Schema(description = "Anh bia hien tai, null khi khong con ban", nullable = true)
    @Nullable String defaultImageKey,
    @Schema(description = "false = van luu nhung tam khong mua duoc") Boolean available,
    @Schema(description = "Ly do khi available = false, null khi mua duoc", nullable = true)
    @Nullable String unavailableReason
) {
}
