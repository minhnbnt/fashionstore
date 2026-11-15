package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PopupAudience;
import com.minhnbnt.fashionstore.model.PopupFrequency;
import com.minhnbnt.fashionstore.model.SitePopup;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record PublicPopupResponse(
    @Schema(description = "Khóa đếm tần suất ở trình duyệt")
    UUID id,

    @Nullable String title,

    @Nullable String body,

    @Schema(description = "URL public ảnh popup, null = popup chỉ có chữ")
    @Nullable String imageUrl,

    @Nullable String ctaLabel,

    @Nullable String linkUrl,

    @Schema(description = "Trình duyệt tự lọc theo trạng thái đăng nhập")
    PopupAudience audience,

    PopupFrequency frequency,

    int delaySeconds
) {

    public static PublicPopupResponse of(SitePopup popup, @Nullable String imageUrl) {
        return new PublicPopupResponse(
            popup.getId(),
            popup.getTitle(),
            popup.getBody(),
            imageUrl,
            popup.getCtaLabel(),
            popup.getLinkUrl(),
            popup.getAudience(),
            popup.getFrequency(),
            popup.getDelaySeconds()
        );
    }
}
