package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.BannerPlacement;
import com.minhnbnt.fashionstore.model.ScheduleState;
import com.minhnbnt.fashionstore.model.SiteBanner;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record BannerResponse(
    UUID id,

    BannerPlacement placement,

    @Schema(description = "Tên banner, dùng làm alt ảnh", example = "Sale thu đông -50%")
    String title,

    @Schema(description = "URL public ảnh desktop")
    String imageUrl,

    @Schema(description = "URL public ảnh mobile, null = dùng ảnh desktop")
    @Nullable String mobileImageUrl,

    @Nullable String linkUrl,

    int sortOrder,

    boolean active,

    @Nullable Instant startsAt,

    @Nullable Instant endsAt,

    @Schema(description = "Trạng thái lịch chạy tính tại thời điểm gọi API")
    ScheduleState scheduleState,

    Instant updatedAt
) {

    public static BannerResponse of(
        SiteBanner banner,
        String imageUrl,
        @Nullable String mobileImageUrl,
        Instant now) {
        return new BannerResponse(
            banner.getId(),
            banner.getPlacement(),
            banner.getTitle(),
            imageUrl,
            mobileImageUrl,
            banner.getLinkUrl(),
            banner.getSortOrder(),
            banner.isActive(),
            banner.getStartsAt(),
            banner.getEndsAt(),
            banner.scheduleStateAt(now),
            banner.getUpdatedAt()
        );
    }
}
