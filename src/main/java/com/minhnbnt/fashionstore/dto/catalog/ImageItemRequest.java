package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageItemRequest(
    @Schema(description = "S3 key đã upload qua presign", example = "abc123.jpg")
    @NotBlank
    String s3Key,

    @Schema(description = "Gắn vào variant cụ thể, null = ảnh chung product", nullable = true)
    @Nullable
    UUID variantId,

    @Schema(description = "Thứ tự hiển thị")
    @Min(0)
    int sortOrder
) {
}
