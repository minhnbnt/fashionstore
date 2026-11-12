package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
    @Schema(description = "ID đánh giá") UUID id,
    @Schema(description = "ID sản phẩm") UUID productId,
    @Schema(description = "ID đơn hàng đã mua", nullable = true) @Nullable UUID orderId,
    @Schema(description = "Tên hiển thị (đã che nếu chọn ẩn danh ở endpoint công khai)") String buyerDisplayName,
    @Schema(description = "Phân loại/kích thước đã mua trong đơn", nullable = true) @Nullable String variantLabel,
    @Schema(description = "Số sao đánh giá (1-5)") int stars,
    @Schema(description = "Nhận xét chi tiết", nullable = true) @Nullable String comment,
    @Schema(description = "Danh sách S3 key ảnh đính kèm") List<String> imageKeys,
    @Schema(description = "S3 key video thực tế", nullable = true) @Nullable String videoKey,
    @Schema(description = "Danh sách tag đánh giá nhanh") List<String> tags,
    @Schema(description = "Có chọn ẩn danh tên hay không") boolean anonymous,
    @Schema(description = "Có phải đánh giá của người dùng hiện tại hay không") boolean mine,
    @Schema(description = "Số lượt nhấn hữu ích") int helpfulCount,
    @Schema(description = "Người dùng hiện tại đã nhấn hữu ích hay chưa") boolean helpfulByMe,
    @Schema(description = "Phản hồi từ shop", nullable = true) @Nullable String sellerReply,
    @Schema(description = "Thời điểm shop phản hồi", nullable = true) @Nullable Instant repliedAt,
    @Schema(description = "Đánh giá đã từng chỉnh sửa hay chưa") boolean edited,
    @Schema(description = "Thời điểm chỉnh sửa", nullable = true) @Nullable Instant editedAt,
    @Schema(description = "Thời điểm tạo đánh giá") Instant createdAt,
    @Schema(description = "Thời điểm cập nhật gần nhất") Instant updatedAt
) {
}
