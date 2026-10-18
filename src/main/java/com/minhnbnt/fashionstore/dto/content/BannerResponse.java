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

    String title,

    @Schema(description = "S3 key ảnh desktop")
    String imageKey,

    @Schema(description = "S3 key ảnh mobile, null = dùng ảnh desktop")
    @Nullable String mobileImageKey,

    @Nullable String linkUrl,

    int sortOrder,

    boolean active,

    @Nullable Instant startsAt,

    @Nullable Instant endsAt,

    @Schema(description = "Trạng thái lịch chạy tính tại thời điểm gọi API")
    ScheduleState scheduleState,

    Instant updatedAt
) {

    public static BannerResponse of(SiteBanner banner, Instant now) {
        return new BannerResponse(
            banner.getId(),
            banner.getPlacement(),
            banner.getTitle(),
            banner.getImageKey(),
            banner.getMobileImageKey(),
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
