package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.PageStatus;
import com.minhnbnt.fashionstore.model.SitePage;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SitePageResponse(
    UUID id,

    String slug,

    String title,

    @Schema(description = "Nội dung Markdown")
    String content,

    @Nullable String metaDescription,

    PageFooterGroup footerGroup,

    int sortOrder,

    PageStatus status,

    @Nullable Instant publishedAt,

    Instant updatedAt
) {

    public static SitePageResponse of(SitePage page) {
        return new SitePageResponse(
            page.getId(),
            page.getSlug(),
            page.getTitle(),
            page.getContent(),
            page.getMetaDescription(),
            page.getFooterGroup(),
            page.getSortOrder(),
            page.getStatus(),
            page.getPublishedAt(),
            page.getUpdatedAt()
        );
    }
}
