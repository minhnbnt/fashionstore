package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CreateProductReviewRequest(
    @NotNull
    UUID productId,

    @Schema(description = "Số sao đánh giá (1-5)", example = "5")
    @NotNull
    @Min(1)
    @Max(5)
    Short rating,

    @Schema(description = "Bình luận đánh giá", nullable = true)
    @Nullable
    String comment,

    @Schema(description = "Mảng S3 keys ảnh đính kèm", nullable = true)
    @Nullable
    List<String> imageKeys
) {
}
