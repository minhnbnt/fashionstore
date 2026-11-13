package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record ReviewSummaryResponse(
    @Schema(description = "Tổng số đánh giá") long reviewCount,
    @Schema(description = "Điểm trung bình", nullable = true) @Nullable Double averageRating,
    @Schema(description = "Số đánh giá 5 sao") long fiveStarCount,
    @Schema(description = "Số đánh giá 4 sao") long fourStarCount,
    @Schema(description = "Số đánh giá 3 sao") long threeStarCount,
    @Schema(description = "Số đánh giá 2 sao") long twoStarCount,
    @Schema(description = "Số đánh giá 1 sao") long oneStarCount,
    @Schema(description = "Số đánh giá có kèm ảnh hoặc video") long withMediaCount
) {
}

