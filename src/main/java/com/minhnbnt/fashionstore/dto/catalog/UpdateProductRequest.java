package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
    @Schema(description = "Tên mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Pattern(regexp = "^(?!\\s*$).+", message = "Product name cannot be blank if provided")
    @Size(min = 5, max = 300, message = "Product name must be between 5 and 300 characters")
    String name,

    @Schema(description = "Mô tả mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Size(max = 20000, message = "Product description cannot exceed 20000 characters")
    String description,

    @Schema(description = "Danh mục mới, null = giữ nguyên", nullable = true)
    @Nullable
    UUID categoryId,

    @Schema(description = "ACTIVE hoặc HIDDEN, null = giữ nguyên", example = "HIDDEN", nullable = true)
    @Nullable
    @Pattern(regexp = "^(ACTIVE|HIDDEN)$", message = "Status must be either ACTIVE or HIDDEN")
    String status,

    @Schema(description = "Sửa variant theo id hoặc thêm mới nếu id null, null = không đụng variants", nullable = true)
    @Nullable
    @Size(min = 1, message = "Product must have at least one variant if variants list is provided")
    @Valid
    List<UpdateVariantRequest> variants,

    @Schema(description = "Danh sách ảnh mới, null = giữ nguyên danh sách ảnh cũ", nullable = true)
    @Nullable
    @Size(max = 9, message = "Product cannot exceed 9 images")
    @Valid
    List<ImageItemRequest> images,

    @Schema(description = "S3 key video giới thiệu sản phẩm (tối đa 60s), null = giữ nguyên", nullable = true)
    @Nullable
    String videoKey,

    @Schema(description = "S3 key ảnh sản phẩm phẳng cho Virtual Try-On, null = giữ nguyên", nullable = true)
    @Nullable
    String garmentKey,

    @Schema(description = "Bật/tắt tính năng thử đồ ảo, null = giữ nguyên", nullable = true)
    @Nullable
    Boolean tryOnEnabled,

    @Schema(description = "Chất liệu sản phẩm, null = giữ nguyên", nullable = true)
    @Nullable
    String material,

    @Schema(description = "Xuất xứ sản phẩm, null = giữ nguyên", nullable = true)
    @Nullable
    String origin,

    @Schema(description = "Hướng dẫn bảo quản, null = giữ nguyên", nullable = true)
    @Nullable
    String careInstructions,

    @Schema(description = "Bảng hướng dẫn chọn size, null = giữ nguyên", nullable = true)
    @Nullable
    String sizeGuide,

    @Schema(description = "Cân nặng đóng gói tính bằng grams", nullable = true)
    @Nullable
    @Positive(message = "Package weight must be positive")
    Integer weightGrams,

    @Schema(description = "Chiều dài đóng gói (cm)", nullable = true)
    @Nullable
    @Positive(message = "Package length must be positive")
    Integer lengthCm,

    @Schema(description = "Chiều rộng đóng gói (cm)", nullable = true)
    @Nullable
    @Positive(message = "Package width must be positive")
    Integer widthCm,

    @Schema(description = "Chiều cao đóng gói (cm)", nullable = true)
    @Nullable
    @Positive(message = "Package height must be positive")
    Integer heightCm
) {
    public UpdateProductRequest(
        @Nullable String name,
        @Nullable String description,
        @Nullable UUID categoryId,
        @Nullable String status,
        @Nullable List<UpdateVariantRequest> variants
    ) {
        this(
            name, description, categoryId, status, variants,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null
        );
    }
}
