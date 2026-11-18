package com.minhnbnt.fashionstore.dto.qna;

import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerQuestionResponse(
    @Schema(description = "ID câu hỏi")
    UUID id,

    @Schema(description = "Mã câu hỏi công khai", example = "Q-123456")
    String code,

    @Schema(description = "ID sản phẩm")
    UUID productId,

    @Schema(description = "Tên sản phẩm")
    String productName,

    @Schema(description = "URL ảnh đại diện sản phẩm", nullable = true)
    @Nullable
    String productImageUrl,

    @Schema(description = "Tên hiển thị người hỏi")
    String askerDisplayName,

    @Schema(description = "Nội dung câu hỏi")
    String content,

    @Schema(description = "Trạng thái câu hỏi (UNANSWERED, ANSWERED, CLOSED)")
    ProductQuestionStatus status,

    @Schema(description = "Tổng số câu trả lời")
    long answerCount,

    @Schema(description = "Số câu trả lời từ phía shop")
    long shopAnswerCount,

    @Schema(description = "Thời điểm đóng câu hỏi", nullable = true)
    @Nullable
    Instant closedAt,

    @Schema(description = "Thời điểm chỉnh sửa", nullable = true)
    @Nullable
    Instant editedAt,

    @Schema(description = "Thời điểm tạo")
    Instant createdAt,

    @Schema(description = "Thời điểm cập nhật gần nhất")
    Instant updatedAt,

    @Schema(description = "Danh sách câu trả lời")
    List<ProductAnswerResponse> answers
) {
}
