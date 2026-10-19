package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.BannerPlacement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record BannerRequest(
    @Schema(description = "Vị trí hiển thị", example = "HOME_CAROUSEL")
    @NotNull
    BannerPlacement placement,

    @Schema(description = "Tên banner, dùng làm alt ảnh", example = "Sale thu đông -50%")
    @NotBlank
    @Size(max = 120)
    String title,

    @Schema(description = "S3 key ảnh desktop (từ /api/uploads/presign)", example = "3f1c....webp")
    @NotBlank
    @Size(max = 255)
    String imageKey,

    @Schema(description = "S3 key ảnh mobile, bỏ trống thì dùng ảnh desktop")
    @Size(max = 255)
    @Nullable String mobileImageKey,

    @Schema(description = "Link khi bấm: đường dẫn nội bộ hoặc URL http(s)", example = "/search?q=ao%20khoac")
    @Size(max = 500)
    @Pattern(regexp = ContentLinks.PATTERN, message = ContentLinks.MESSAGE)
    @Nullable String linkUrl,

    @Schema(description = "Thứ tự trong vị trí, nhỏ lên trước", example = "1")
    @Min(0)
    @Max(9999)
    int sortOrder,

    @Schema(description = "Bật/tắt hiển thị")
    boolean active,

    @Schema(description = "Bắt đầu hiển thị, bỏ trống = ngay")
    @Nullable Instant startsAt,

    @Schema(description = "Ngừng hiển thị, bỏ trống = không hạn, phải sau startsAt")
    @Nullable Instant endsAt
) {
}
