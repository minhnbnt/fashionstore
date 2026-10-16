package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CreateReturnRequest(
    @Schema(description = "Lý do đổi trả (WRONG_ITEM, DAMAGED, NOT_AS_DESCRIBED, CHANGE_MIND)", example = "DAMAGED")
    @NotBlank
    String reason,

    @Schema(description = "Mô tả chi tiết lỗi/vấn đề", nullable = true)
    @Nullable
    String description,

    @Schema(description = "Mảng S3 keys ảnh hoặc video chứng minh", nullable = true)
    @Nullable
    List<String> evidenceKeys
) {
}
