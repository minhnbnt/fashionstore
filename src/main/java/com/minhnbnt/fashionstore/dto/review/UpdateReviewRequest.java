package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateReviewRequest(
    @Schema(description = "Diem 1-5", example = "4")
    @Min(1)
    @Max(5)
    int stars,

    @Schema(description = "Nhan xet, null neu chi cham diem", nullable = true)
    @Nullable
    @Size(max = 2000)
    String comment
) {
}
