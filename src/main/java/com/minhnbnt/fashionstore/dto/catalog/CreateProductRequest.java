package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
    @Schema(example = "Áo thun đen")
    @NotBlank
    @Size(max = 300)
    String name,

    @Schema(description = "Slug duy nhất trong shop", example = "ao-thun-den")
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$")
    String slug,

    @Schema(nullable = true)
    @Size(max = 5000)
    @Nullable
    String description,

    @Schema(description = "Danh mục, null nếu chưa phân loại", nullable = true)
    @Nullable
    UUID categoryId,

    @Schema(description = "Ít nhất 1 variant, đúng 1 default")
    @NotEmpty
    @Valid
    List<CreateVariantRequest> variants
) {
}
