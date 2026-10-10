package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
    @Schema(description = "Tên mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Size(max = 300)
    String name,

    @Schema(description = "Mô tả mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Size(max = 5000)
    String description,

    @Schema(description = "Danh mục mới, null = giữ nguyên", nullable = true)
    @Nullable
    UUID categoryId,

    @Schema(description = "ACTIVE hoặc HIDDEN, null = giữ nguyên", example = "HIDDEN", nullable = true)
    @Nullable
    @Pattern(regexp = "^(ACTIVE|HIDDEN)$")
    String status,

    @Schema(description = "Sửa variant theo id, null = không đụng variants", nullable = true)
    @Nullable
    @Valid
    List<UpdateVariantRequest> variants
) {
}
