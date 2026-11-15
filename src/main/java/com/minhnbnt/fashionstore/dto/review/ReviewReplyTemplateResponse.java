package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Mẫu phản hồi nhanh đánh giá của shop")
public record ReviewReplyTemplateResponse(
    @Schema(description = "ID mẫu") UUID id,
    @Schema(description = "ID shop") UUID shopId,
    @Schema(description = "Tiêu đề ngắn") String title,
    @Schema(description = "Nội dung văn bản thuần") String content,
    @Schema(description = "Thời điểm tạo") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật gần nhất") Instant updatedAt
) {
}

