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

    @Schema(description = "S3 key của avatar, null nếu chưa có. Frontend tự ghép với base URL S3.")
    @Nullable
    String avatarKey,

    Instant createdAt
) {
}
