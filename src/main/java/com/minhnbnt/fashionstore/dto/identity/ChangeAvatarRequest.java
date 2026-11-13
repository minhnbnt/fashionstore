package com.minhnbnt.fashionstore.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeAvatarRequest(
    @Schema(
        description = "S3 key ảnh đã upload qua /api/uploads/presign",
        example = "3f1c1a6e-2b7d-4c1e-9f0a-8d2c4b6a1e33.png"
    )
    @NotBlank
    @Pattern(regexp = AVATAR_KEY_REGEX, message = "avatarKey must be an uploaded image key")
    String avatarKey
) {

    /**
     * Chỉ nhận key do {@code /api/uploads/presign} sinh ra, và chỉ đuôi ảnh trình duyệt render được.
     * Bỏ svg vì bucket public read, mở thẳng URL thì script trong file chạy được.
     */
    private static final String AVATAR_KEY_REGEX =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            + "\\.(png|jpe?g|webp|gif|avif)$";
}
