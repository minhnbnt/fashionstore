package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Trang tĩnh (giới thiệu, chính sách...). Nội dung Markdown, public tại {@code /pages/{slug}} khi đã xuất bản.
 */
@Table("site_page")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class SitePage implements Persistable<UUID> {

    @Id
    private UUID id;

    private String slug;

    private String title;

    private String content;

    private @Nullable String metaDescription;

    private PageFooterGroup footerGroup;

    private int sortOrder;

    private PageStatus status;

    private @Nullable Instant publishedAt;

    private @Nullable UUID updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    /**
     * Trang mới luôn là nháp, admin xem lại rồi mới xuất bản.
     */
    public static SitePage create(
        String slug,
        String title,
        String content,
        @Nullable String metaDescription,
        PageFooterGroup footerGroup,
        int sortOrder,
        UUID adminId) {
        var now = Instant.now();
        var page = new SitePage(
            UUID.randomUUID(),
            slug,
            title,
            content,
            null,
            footerGroup,
            sortOrder,
            PageStatus.DRAFT,
            null,
            adminId,
            now,
            now,
            true);
        page.update(slug, title, content, metaDescription, footerGroup, sortOrder, adminId);
        return page;
    }

    public void update(
        String slug,
        String title,
        String content,
        @Nullable String metaDescription,
        PageFooterGroup footerGroup,
        int sortOrder,
        UUID adminId) {
        this.slug = slug;
        this.title = title.trim();
        this.content = content;
        this.metaDescription = ContentFields.optional(metaDescription);
        this.footerGroup = footerGroup;
        this.sortOrder = sortOrder;
        this.updatedBy = adminId;
        this.updatedAt = Instant.now();
    }

    public void publish(UUID adminId) {
        if (status == PageStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Page is already published");
        }

        var now = Instant.now();
        status = PageStatus.PUBLISHED;
        publishedAt = now;
        updatedBy = adminId;
        updatedAt = now;
    }

    public void unpublish(UUID adminId) {
        if (status != PageStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Page is not published");
        }

        status = PageStatus.DRAFT;
        publishedAt = null;
        updatedBy = adminId;
        updatedAt = Instant.now();
    }

    /**
     * Gỡ xuất bản trước rồi mới xóa, tránh vô tình làm gãy link đang chạy ở footer.
     */
    public void requireDeletable() {
        if (status == PageStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unpublish the page before deleting it");
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
