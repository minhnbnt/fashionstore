package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReportReviewRequest(
    @Schema(description = "Lý do báo cáo đánh giá vi phạm", example = "Đánh giá xúc phạm, sai sự thật")
    @NotBlank
    String reason
) {
}
