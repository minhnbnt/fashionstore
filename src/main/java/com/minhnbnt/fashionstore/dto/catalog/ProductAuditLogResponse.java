package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ProductAuditLogResponse(
    @Schema(description = "Id bản ghi audit") UUID id,
    @Schema(description = "Id sản phẩm") UUID productId,
    @Schema(description = "Id gian hàng") UUID shopId,
    @Schema(description = "Id người thực hiện", nullable = true) @Nullable UUID changedBy,
    @Schema(description = "Hành động thực hiện") String action,
    @Schema(description = "Tóm tắt thay đổi") String changesSummary,
    @Schema(description = "Dữ liệu trước thay đổi (JSON)", nullable = true) @Nullable String oldValues,
    @Schema(description = "Dữ liệu sau thay đổi (JSON)", nullable = true) @Nullable String newValues,
    @Schema(description = "Thời điểm thay đổi") Instant createdAt
) {
}
