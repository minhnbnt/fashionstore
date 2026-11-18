package com.minhnbnt.fashionstore.dto.qna;

import com.minhnbnt.fashionstore.model.ProductAnswerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ProductAnswerResponse(
    @Schema(description = "ID câu trả lời")
    UUID id,

    @Schema(description = "ID câu hỏi")
    UUID questionId,

    @Schema(description = "Tên hiển thị người trả lời")
    String authorDisplayName,

    @Schema(description = "Vai trò người trả lời (SHOP hoặc VERIFIED_BUYER)")
    ProductAnswerRole authorRole,

    @Schema(description = "Có phải người mua đã xác thực")
    boolean verifiedBuyer,

    @Schema(description = "Nội dung câu trả lời")
    String content,

    @Schema(description = "Câu trả lời do chính người dùng hiện tại viết")
    boolean mine,

    @Schema(description = "Người dùng hiện tại có quyền sửa câu trả lời này")
    boolean canEdit,

    @Schema(description = "Người dùng hiện tại có quyền xóa câu trả lời này")
    boolean canDelete,

    @Schema(description = "Thời điểm chỉnh sửa", nullable = true)
    @Nullable
    Instant editedAt,

    @Schema(description = "Thời điểm tạo")
    Instant createdAt,

    @Schema(description = "Thời điểm cập nhật gần nhất")
    Instant updatedAt
) {
}
