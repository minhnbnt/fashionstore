package com.minhnbnt.fashionstore.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    @Schema(description = "id = GoTrue sub")
    UUID id,

    @Schema(example = "an@t.local")
    String email,

    @Schema(example = "An")
    String displayName,

    @Schema(description = "URL avatar public path-style, null nếu chưa có. Không trả S3 key.")
    @Nullable
    String avatarUrl,

    @Schema(description = "Role trên sàn", example = "CUSTOMER")
    String role,

    @Schema(description = "ACTIVE hoặc LOCKED", example = "ACTIVE")
    String status,

    @Schema(description = "Lý do khóa, null khi ACTIVE", nullable = true)
    @Nullable
    String statusReason,

    Instant createdAt
) {
}
