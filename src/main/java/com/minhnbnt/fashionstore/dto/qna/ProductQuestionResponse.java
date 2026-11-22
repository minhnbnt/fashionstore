package com.minhnbnt.fashionstore.dto.qna;

import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductQuestionResponse(
    @Schema(description = "ID câu hỏi")
    UUID id,

    @Schema(description = "Mã câu hỏi công khai", example = "QA-A1B2C3D4E5F6")
    String code,

    @Schema(description = "ID sản phẩm")
    UUID productId,

    @Schema(description = "Tên hiển thị người hỏi")
    String askerDisplayName,

    @Schema(description = "Nội dung câu hỏi")
    String content,

    @Schema(description = "Trạng thái câu hỏi (UNANSWERED, ANSWERED, CLOSED)")
    ProductQuestionStatus status,

    @Schema(description = "Câu hỏi do chính người dùng hiện tại hỏi")
    boolean mine,

    @Schema(description = "Người dùng hiện tại có quyền sửa câu hỏi này")
    boolean canEdit,

    @Schema(description = "Người dùng hiện tại có quyền xóa câu hỏi này")
    boolean canDelete,

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
