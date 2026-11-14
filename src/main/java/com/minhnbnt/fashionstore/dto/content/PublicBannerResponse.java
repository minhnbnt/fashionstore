package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.SiteBanner;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record PublicBannerResponse(
    UUID id,

    @Schema(description = "Dùng làm alt ảnh")
    String title,

    @Schema(description = "URL public ảnh desktop")
    String imageUrl,

    @Schema(description = "URL public ảnh mobile, null = dùng ảnh desktop")
    @Nullable String mobileImageUrl,

    @Nullable String linkUrl
) {

    public static PublicBannerResponse of(
        SiteBanner banner,
        String imageUrl,
        @Nullable String mobileImageUrl) {
        return new PublicBannerResponse(
            banner.getId(),
            banner.getTitle(),
            imageUrl,
            mobileImageUrl,
            banner.getLinkUrl()
        );
    }
}
