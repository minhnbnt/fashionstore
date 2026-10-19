package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.PageStatus;
import com.minhnbnt.fashionstore.model.SitePage;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Dòng trong danh sách trang của admin, không kèm nội dung.
 */
public record SitePageSummaryResponse(
    UUID id,

    String slug,

    String title,

    PageFooterGroup footerGroup,

    int sortOrder,

    PageStatus status,

    @Nullable Instant publishedAt,

    Instant updatedAt
) {

    public static SitePageSummaryResponse of(SitePage page) {
        return new SitePageSummaryResponse(
            page.getId(),
            page.getSlug(),
            page.getTitle(),
            page.getFooterGroup(),
            page.getSortOrder(),
            page.getStatus(),
            page.getPublishedAt(),
            page.getUpdatedAt()
        );
    }
}
