package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SellerReviewResponse(
    @Schema(description = "ID đánh giá") UUID id,
    @Schema(description = "ID sản phẩm") UUID productId,
    @Schema(description = "Tên hiển thị người mua") String buyerDisplayName,
    @Schema(description = "Số sao đánh giá (1-5)") int stars,
    @Schema(nullable = true) @Nullable String comment,
    @Schema(nullable = true) @Nullable String sellerReply,
    @Schema(nullable = true) @Nullable Instant repliedAt,
    @Schema(description = "Đánh giá đã bị shop báo cáo hay chưa") boolean isReported,
    @Schema(description = "Đánh giá đã từng được chỉnh sửa hay chưa") boolean edited,
    @Schema(nullable = true) @Nullable Instant editedAt,
    @Schema(description = "Thời điểm tạo đánh giá") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật gần nhất") Instant updatedAt
) {
}
