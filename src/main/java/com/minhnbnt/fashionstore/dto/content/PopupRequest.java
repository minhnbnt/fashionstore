package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PopupAudience;
import com.minhnbnt.fashionstore.model.PopupFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record PopupRequest(
    @Schema(description = "Tên nội bộ cho admin", example = "Popup 11.11")
    @NotBlank
    @Size(max = 120)
    String name,

    @Schema(description = "Tiêu đề hiện trên popup. Cần tiêu đề hoặc ảnh", example = "Giảm 50% toàn sàn")
    @Size(max = 120)
    @Nullable String title,

    @Schema(description = "Mô tả ngắn dưới tiêu đề")
    @Size(max = 500)
    @Nullable String body,

    @Schema(description = "S3 key ảnh popup. Cần tiêu đề hoặc ảnh")
    @Size(max = 255)
    @Nullable String imageKey,

    @Schema(description = "Chữ trên nút CTA", example = "Mua ngay")
    @Size(max = 40)
    @Nullable String ctaLabel,

    @Schema(description = "Link nút CTA/ảnh: đường dẫn nội bộ hoặc URL http(s)", example = "/search?q=sale")
    @Size(max = 500)
    @Pattern(regexp = ContentLinks.PATTERN, message = ContentLinks.MESSAGE)
    @Nullable String linkUrl,

    @Schema(description = "Ai thấy popup", example = "ALL")
    @NotNull
    PopupAudience audience,

    @Schema(description = "Tần suất hiện trên cùng trình duyệt", example = "ONCE_PER_DAY")
    @NotNull
    PopupFrequency frequency,

    @Schema(description = "Chờ bao nhiêu giây sau khi vào trang mới hiện", example = "3")
    @Min(0)
    @Max(60)
    int delaySeconds,

    @Schema(description = "Ưu tiên, lớn thắng khi nhiều popup cùng hợp lệ", example = "10")
    @Min(0)
    @Max(1000)
    int priority,

    @Schema(description = "Bật/tắt")
    boolean active,

    @Schema(description = "Bắt đầu hiển thị, bỏ trống = ngay")
    @Nullable Instant startsAt,

    @Schema(description = "Ngừng hiển thị, bỏ trống = không hạn, phải sau startsAt")
    @Nullable Instant endsAt
) {
}
