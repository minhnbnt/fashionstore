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

    @Schema(description = "Mô tả chi tiết rich text", nullable = true)
    @Size(max = 20000)
    @Nullable
    String description,

    @Schema(description = "Danh mục, null nếu chưa phân loại", nullable = true)
    @Nullable
    UUID categoryId,

    @Schema(description = "Ít nhất 1 variant, đúng 1 default")
    @NotEmpty
    @Valid
    List<CreateVariantRequest> variants,

    @Schema(description = "Danh sách ảnh sản phẩm (tối thiểu 3, tối đa 9 ảnh)", nullable = true)
    @Nullable
    @Valid
    List<ImageItemRequest> images,

    @Schema(description = "S3 key video giới thiệu sản phẩm (tối đa 60s)", nullable = true)
    @Nullable
    String videoKey,

    @Schema(description = "S3 key ảnh sản phẩm phẳng (nền trắng) cho Virtual Try-On", nullable = true)
    @Nullable
    String garmentKey,

    @Schema(description = "Bật tính năng thử đồ ảo (Virtual Try-On)", nullable = true)
    @Nullable
    Boolean tryOnEnabled,

    @Schema(description = "Chất liệu sản phẩm", nullable = true, example = "100% Cotton")
    @Nullable
    String material,

    @Schema(description = "Xuất xứ sản phẩm", nullable = true, example = "Việt Nam")
    @Nullable
    String origin,

    @Schema(description = "Hướng dẫn bảo quản", nullable = true, example = "Giặt máy ở nhiệt độ thường")
    @Nullable
    String careInstructions,

    @Schema(description = "Bảng hướng dẫn chọn size", nullable = true)
    @Nullable
    String sizeGuide,

    @Schema(description = "Cân nặng đóng gói tính bằng grams", nullable = true, example = "250")
    @Nullable
    Integer weightGrams,

    @Schema(description = "Chiều dài đóng gói (cm)", nullable = true, example = "25")
    @Nullable
    Integer lengthCm,

    @Schema(description = "Chiều rộng đóng gói (cm)", nullable = true, example = "18")
    @Nullable
    Integer widthCm,

    @Schema(description = "Chiều cao đóng gói (cm)", nullable = true, example = "5")
    @Nullable
    Integer heightCm,

    @Schema(description = "Trạng thái mong muốn khi đăng bán: ACTIVE (Đang bán) hoặc HIDDEN (Tạm ẩn)", nullable = true, example = "ACTIVE")
    @Nullable
    String status
) {
    public CreateProductRequest(
        String name,
        String slug,
        @Nullable String description,
        @Nullable UUID categoryId,
        List<CreateVariantRequest> variants
    ) {
        this(
            name, slug, description, categoryId, variants,
            null, null, null, false,
            null, null, null, null,
            null, null, null, null,
            null
        );
    }
}
