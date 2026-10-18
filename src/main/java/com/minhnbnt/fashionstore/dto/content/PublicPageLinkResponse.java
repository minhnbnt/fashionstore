package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.SitePage;

/**
 * Link trang đã xuất bản để dựng footer.
 */
public record PublicPageLinkResponse(
    String slug,

    String title,

    PageFooterGroup footerGroup
) {

    public static PublicPageLinkResponse of(SitePage page) {
        return new PublicPageLinkResponse(page.getSlug(), page.getTitle(), page.getFooterGroup());
    }
}
