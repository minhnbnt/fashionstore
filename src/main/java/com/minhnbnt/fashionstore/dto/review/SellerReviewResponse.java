package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerReviewResponse(
    @Schema(description = "ID đánh giá") UUID id,
    @Schema(description = "ID sản phẩm") UUID productId,
    @Schema(description = "ID đơn hàng", nullable = true) @Nullable UUID orderId,
    @Schema(description = "Tên hiển thị người mua (đã che nếu khách chọn ẩn danh)") String buyerDisplayName,
    @Schema(description = "Phân loại/kích thước khách đã mua", nullable = true) @Nullable String variantLabel,
    @Schema(description = "Số sao đánh giá (1-5)") int stars,
    @Schema(nullable = true) @Nullable String comment,
    @Schema(description = "Danh sách S3 key ảnh thực tế") List<String> imageKeys,
    @Schema(description = "S3 key video thực tế", nullable = true) @Nullable String videoKey,
    @Schema(description = "Danh sách tag đánh giá nhanh") List<String> tags,
    @Schema(description = "Khách có bật ẩn danh hay không") boolean anonymous,
    @Schema(description = "Số lượt hữu ích") int helpfulCount,
    @Schema(nullable = true) @Nullable String sellerReply,
    @Schema(nullable = true) @Nullable Instant repliedAt,
    @Schema(description = "Đánh giá đã bị shop báo cáo hay chưa") boolean isReported,
    @Schema(description = "Đánh giá đã từng được chỉnh sửa hay chưa") boolean edited,
    @Schema(nullable = true) @Nullable Instant editedAt,
    @Schema(description = "Thời điểm tạo đánh giá") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật gần nhất") Instant updatedAt
) {
}
