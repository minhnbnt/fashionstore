package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record RatingSummary(
    @Schema(description = "So luot danh gia") long reviewCount,
    @Schema(description = "Diem trung binh, null khi chua ai danh gia", nullable = true)
    @Nullable Double averageRating
) {
}
