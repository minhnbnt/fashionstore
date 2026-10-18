package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record OrderTimelineResponse(
    @Schema(description = "Trạng thái trước, null ở bước tạo đơn", nullable = true, example = "PENDING")
    @Nullable
    String fromStatus,

    @Schema(example = "CONFIRMED")
    String toStatus,

    @Schema(description = "Vai trò người thao tác: BUYER | SELLER | ADMIN | SYSTEM", example = "SELLER")
    String actor,

    @Schema(nullable = true)
    @Nullable
    UUID changedBy,

    @Schema(nullable = true)
    @Nullable
    String changedByEmail,

    @Schema(description = "Ghi chú kèm thao tác, rỗng nếu không có")
    String note,

    Instant createdAt
) {
}
