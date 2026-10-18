package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Banner admin đặt theo vị trí trên storefront, có lịch chạy. Ảnh lưu S3 key.
 */
@Table("site_banner")
@Getter
public class SiteBanner implements Persistable<UUID> {

    @Id
    private UUID id;

    private BannerPlacement placement;

    private String title;

    private String imageKey;

    private @Nullable String mobileImageKey;

    private @Nullable String linkUrl;

    private int sortOrder;

    private boolean active;

    private @Nullable Instant startsAt;

    private @Nullable Instant endsAt;

    private @Nullable UUID updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public SiteBanner(
        UUID id,
        BannerPlacement placement,
        String title,
        String imageKey,
        @Nullable String mobileImageKey,
        @Nullable String linkUrl,
        int sortOrder,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        @Nullable UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.placement = placement;
        this.title = title;
        this.imageKey = imageKey;
        this.mobileImageKey = mobileImageKey;
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.active = active;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static SiteBanner create(
        BannerPlacement placement,
        String title,
        String imageKey,
        @Nullable String mobileImageKey,
        @Nullable String linkUrl,
        int sortOrder,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        UUID adminId
    ) {
        var now = Instant.now();
        var banner = new SiteBanner(
            UUID.randomUUID(), placement, title, imageKey, null, null, 0, false, null, null, adminId, now, now
        );
        banner.update(
            placement, title, imageKey, mobileImageKey, linkUrl, sortOrder, active, startsAt, endsAt, adminId
        );
        banner.newRecord = true;
        return banner;
    }

    public void update(
        BannerPlacement placement,
        String title,
        String imageKey,
        @Nullable String mobileImageKey,
        @Nullable String linkUrl,
        int sortOrder,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        UUID adminId
    ) {
        ContentFields.requireValidWindow(startsAt, endsAt);

        this.placement = placement;
        this.title = title.trim();
        this.imageKey = imageKey.trim();
        this.mobileImageKey = ContentFields.optional(mobileImageKey);
        this.linkUrl = ContentFields.optional(linkUrl);
        this.sortOrder = sortOrder;
        this.active = active;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedBy = adminId;
        this.updatedAt = Instant.now();
    }

    public ScheduleState scheduleStateAt(Instant now) {
        return ScheduleState.of(active, startsAt, endsAt, now);
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
