package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
    @Schema(description = "id = GoTrue sub")
    UUID id,

    @Schema(example = "an@t.local")
    String email,

    @Schema(example = "An")
    String displayName,

    @Schema(description = "S3 key avatar", nullable = true)
    @Nullable
    String avatarKey,

    @Schema(example = "CUSTOMER")
    String role,

    @Schema(example = "ACTIVE")
    String status,

    @Schema(description = "Lý do khóa hiện tại", nullable = true)
    @Nullable
    String statusReason,

    Instant createdAt
) {
}
