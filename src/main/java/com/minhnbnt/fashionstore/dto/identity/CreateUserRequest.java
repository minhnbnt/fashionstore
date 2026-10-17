package com.minhnbnt.fashionstore.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateUserRequest(
    @Schema(description = "Email hiển thị của user", example = "an@t.local")
    @Email
    @NotBlank
    @Size(max = 255)
    String email,

    @Schema(description = "Tên hiển thị", example = "An")
    @NotBlank
    @Size(max = 100)
    String displayName,

    @Schema(description = "S3 key của avatar, null nếu chưa có", nullable = true)
    @Nullable
    String avatarKey
) {
}
