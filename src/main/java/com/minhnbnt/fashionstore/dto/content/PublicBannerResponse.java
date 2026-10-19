package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.SiteBanner;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record PublicBannerResponse(
    UUID id,

    @Schema(description = "Dùng làm alt ảnh")
    String title,

    String imageKey,

    @Schema(description = "null = dùng ảnh desktop")
    @Nullable String mobileImageKey,

    @Nullable String linkUrl
) {

    public static PublicBannerResponse of(SiteBanner banner) {
        return new PublicBannerResponse(
            banner.getId(),
            banner.getTitle(),
            banner.getImageKey(),
            banner.getMobileImageKey(),
            banner.getLinkUrl()
        );
    }
}
