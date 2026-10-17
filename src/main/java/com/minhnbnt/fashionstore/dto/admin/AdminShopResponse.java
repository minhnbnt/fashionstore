package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminShopResponse(
    UUID id,

    String name,

    String slug,

    String description,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String bannerKey,

    @Schema(example = "PENDING")
    String status,

    @Schema(description = "Lý do từ chối/tạm khóa gần nhất", nullable = true)
    @Nullable
    String statusReason,

    @Schema(description = "id owner, null nếu shop mất owner", nullable = true)
    @Nullable
    UUID ownerId,

    @Schema(nullable = true, example = "an@t.local")
    @Nullable
    String ownerEmail,

    @Schema(nullable = true, example = "An")
    @Nullable
    String ownerName,

    @Schema(description = "Trạng thái tài khoản owner", nullable = true, example = "ACTIVE")
    @Nullable
    String ownerStatus,

    @Schema(description = "Lần nộp hồ sơ gần nhất, hàng đợi duyệt xếp theo cột này")
    Instant submittedAt,

    @Schema(nullable = true)
    @Nullable
    Instant approvedAt,

    Instant createdAt,

    Instant updatedAt
) {
}
