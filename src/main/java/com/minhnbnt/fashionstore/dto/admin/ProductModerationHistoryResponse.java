package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ProductModerationHistoryResponse(
    @Schema(example = "ACTIVE")
    String fromStatus,

    @Schema(example = "BANNED")
    String toStatus,

    @Schema(description = "Loại vi phạm khi gỡ, null khi khôi phục", nullable = true, example = "COUNTERFEIT")
    @Nullable
    String violationType,

    @Schema(description = "id admin thao tác", nullable = true)
    @Nullable
    UUID changedBy,

    @Schema(description = "Email admin thao tác", nullable = true)
    @Nullable
    String changedByEmail,

    @Schema(description = "Lý do, rỗng nếu không có")
    String reason,

    Instant createdAt
) {
}
