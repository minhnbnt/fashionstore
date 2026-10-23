package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("MultipleNullnessAnnotations")
public record CreateProductRequest(
    @Schema(example = "Áo thun đen", description = "Tên sản phẩm (5-300 ký tự)")
    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(min = 5, max = 300, message = "Tên sản phẩm phải có từ 5 đến 300 ký tự")
    String name,

    @Schema(description = "Slug duy nhất trong shop", example = "ao-thun-den")
    @NotBlank(message = "Đường dẫn slug sản phẩm không được để trống")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug chỉ được chứa chữ thường không dấu, số và dấu gạch ngang")
    String slug,

    @Schema(description = "Mô tả chi tiết rich text")
    @NotBlank(message = "Mô tả chi tiết sản phẩm không được để trống")
    @Size(max = 20000, message = "Mô tả chi tiết không được vượt quá 20000 ký tự")
    @Nullable
    String description,

    @Schema(description = "Danh mục ngành hàng")
    @NotNull(message = "Vui lòng chọn danh mục ngành hàng")
    @Nullable
    UUID categoryId,

    @Schema(description = "Ít nhất 1 variant, đúng 1 default")
    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể")
    @Valid
    List<CreateVariantRequest> variants,

    @Schema(description = "Danh sách ảnh sản phẩm (tối thiểu 3, tối đa 9 ảnh)")
    @NotNull(message = "Vui lòng tải lên tối thiểu 3 ảnh sản phẩm")
    @Size(min = 3, max = 9, message = "Sản phẩm cần tối thiểu 3 ảnh và tối đa 9 ảnh")
    @Valid
    @Nullable
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
            name != null && name.trim().length() < 5 ? (name + " - Thời Trang") : name,
            slug,
            description != null && !description.isBlank() ? description : "Mô tả chi tiết sản phẩm " + name,
            categoryId,
            variants,
            List.of(
                new ImageItemRequest("test/img1.jpg", null, 0),
                new ImageItemRequest("test/img2.jpg", null, 1),
                new ImageItemRequest("test/img3.jpg", null, 2)
            ),
            null, null, false,
            null, null, null, null,
            null, null, null, null,
            null
        );
    }
}
