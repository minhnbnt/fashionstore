package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
    @Schema(description = "Trạng thái trước, null nếu là bản ghi tạo mới", nullable = true, example = "PENDING")
    @Nullable
    String fromStatus,

    @Schema(example = "APPROVED")
    String toStatus,

    @Schema(description = "id người thao tác", nullable = true)
    @Nullable
    UUID changedBy,

    @Schema(description = "Email người thao tác", nullable = true)
    @Nullable
    String changedByEmail,

    @Schema(description = "Lý do, rỗng nếu không có")
    String reason,

    Instant createdAt
) {
}
