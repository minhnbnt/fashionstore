package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageItemRequest(
    @Schema(description = "Upload id đã presign và PUT file lên S3")
    @NotNull
    UUID uploadId,

    @Schema(description = "Gắn vào variant cụ thể, null = ảnh chung product", nullable = true)
    @Nullable
    UUID variantId,

    @Schema(description = "Thứ tự hiển thị")
    @Min(0)
    int sortOrder
) {
}
