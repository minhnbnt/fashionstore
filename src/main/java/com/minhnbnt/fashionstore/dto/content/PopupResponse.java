package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PopupAudience;
import com.minhnbnt.fashionstore.model.PopupFrequency;
import com.minhnbnt.fashionstore.model.ScheduleState;
import com.minhnbnt.fashionstore.model.SitePopup;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PopupResponse(
    UUID id,

    String name,

    @Nullable String title,

    @Nullable String body,

    @Nullable String imageKey,

    @Nullable String ctaLabel,

    @Nullable String linkUrl,

    PopupAudience audience,

    PopupFrequency frequency,

    int delaySeconds,

    int priority,

    boolean active,

    @Nullable Instant startsAt,

    @Nullable Instant endsAt,

    @Schema(description = "Trạng thái lịch chạy tính tại thời điểm gọi API")
    ScheduleState scheduleState,

    Instant updatedAt
) {

    public static PopupResponse of(SitePopup popup, Instant now) {
        return new PopupResponse(
            popup.getId(),
            popup.getName(),
            popup.getTitle(),
            popup.getBody(),
            popup.getImageKey(),
            popup.getCtaLabel(),
            popup.getLinkUrl(),
            popup.getAudience(),
            popup.getFrequency(),
            popup.getDelaySeconds(),
            popup.getPriority(),
            popup.isActive(),
            popup.getStartsAt(),
            popup.getEndsAt(),
            popup.scheduleStateAt(now),
            popup.getUpdatedAt()
        );
    }
}
