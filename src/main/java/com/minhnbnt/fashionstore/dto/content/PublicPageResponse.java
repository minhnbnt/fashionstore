package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.SitePage;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record PublicPageResponse(
    String slug,

    String title,

    @Schema(description = "Nội dung Markdown, render không cho HTML thô")
    String content,

    @Nullable String metaDescription,

    @Schema(description = "Lần cập nhật cuối, hiện \"Cập nhật ngày...\" cho trang chính sách")
    Instant updatedAt
) {

    public static PublicPageResponse of(SitePage page) {
        return new PublicPageResponse(
            page.getSlug(),
            page.getTitle(),
            page.getContent(),
            page.getMetaDescription(),
            page.getUpdatedAt()
        );
    }
}
