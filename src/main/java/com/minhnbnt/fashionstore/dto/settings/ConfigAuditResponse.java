package com.minhnbnt.fashionstore.dto.settings;

import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ConfigAuditResponse(
    @Schema(description = "id dòng lịch sử")
    UUID id,

    @Schema(description = "Loại đối tượng cấu hình", example = "SETTING")
    ConfigAuditEntity entity,

    @Schema(description = "Khóa đối tượng (key cấu hình, mã hãng, mã cổng...)", example = "shipping.free_threshold")
    String entityKey,

    @Schema(description = "Hành động", example = "UPDATE")
    String action,

    @Schema(description = "Giá trị cũ dạng JSON, null nếu không có", example = "\"500000\"")
    @Nullable String oldValue,

    @Schema(description = "Giá trị mới dạng JSON, null nếu không có", example = "\"300000\"")
    @Nullable String newValue,

    @Schema(description = "id admin thực hiện, null nếu tài khoản đã bị xóa")
    @Nullable UUID changedBy,

    @Schema(description = "Thời điểm thay đổi")
    Instant changedAt
) {
}
