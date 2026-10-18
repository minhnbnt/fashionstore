package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.PublicPageLinkResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPageResponse;
import com.minhnbnt.fashionstore.dto.content.SitePageRequest;
import com.minhnbnt.fashionstore.dto.content.SitePageResponse;
import com.minhnbnt.fashionstore.dto.content.SitePageSummaryResponse;
import com.minhnbnt.fashionstore.features.content.repository.SitePageRepository;
import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.SitePage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Trang tĩnh: admin soạn nháp → xuất bản; public chỉ thấy trang đã xuất bản.
 */
@Service
@RequiredArgsConstructor
public class SitePageService {

    private final SitePageRepository pages;

    @Transactional(readOnly = true)
    public List<SitePageSummaryResponse> listForAdmin() {
        return pages.findAllByOrderByUpdatedAtDesc().stream()
            .map(SitePageSummaryResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public SitePageResponse getForAdmin(@NonNull UUID pageId) {
        return SitePageResponse.of(requirePage(pageId));
    }

    @Transactional
    public SitePageResponse create(@NonNull UUID adminId, @NonNull SitePageRequest request) {
        if (pages.existsBySlug(request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug is already in use");
        }

        var page = SitePage.create(
            request.slug(),
            request.title(),
            request.content(),
            request.metaDescription(),
            request.footerGroup(),
            request.sortOrder(),
            adminId
        );

        return SitePageResponse.of(pages.save(page));
    }

    @Transactional
    public SitePageResponse update(@NonNull UUID adminId, @NonNull UUID pageId, @NonNull SitePageRequest request) {
        var page = requirePage(pageId);
        if (pages.existsBySlugOnOtherPage(request.slug(), pageId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug is already in use");
        }

        page.update(
            request.slug(),
            request.title(),
            request.content(),
            request.metaDescription(),
            request.footerGroup(),
            request.sortOrder(),
            adminId
        );

        return SitePageResponse.of(pages.save(page));
    }

    @Transactional
    public SitePageResponse publish(@NonNull UUID adminId, @NonNull UUID pageId) {
        var page = requirePage(pageId);

        page.publish(adminId);

        return SitePageResponse.of(pages.save(page));
    }

    @Transactional
    public SitePageResponse unpublish(@NonNull UUID adminId, @NonNull UUID pageId) {
        var page = requirePage(pageId);

        page.unpublish(adminId);

        return SitePageResponse.of(pages.save(page));
    }

    @Transactional
    public void delete(@NonNull UUID pageId) {
        var page = requirePage(pageId);

        page.requireDeletable();

        pages.delete(page);
    }

    /**
     * Link footer: bỏ trang {@code NONE} (chỉ vào bằng link trực tiếp).
     */
    @Transactional(readOnly = true)
    public List<PublicPageLinkResponse> publishedLinks() {
        return pages.findPublished().stream()
            .filter(page -> page.getFooterGroup() != PageFooterGroup.NONE)
            .map(PublicPageLinkResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public PublicPageResponse getPublished(@NonNull String slug) {
        var page = pages.findPublishedBySlug(slug).orElse(null);
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return PublicPageResponse.of(page);
    }

    private SitePage requirePage(UUID pageId) {
        var page = pages.findById(pageId).orElse(null);
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return page;
    }
}
