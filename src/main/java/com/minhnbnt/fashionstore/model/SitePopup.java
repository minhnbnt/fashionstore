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
 * Popup khuyến mãi trang chủ. Backend chỉ lọc lịch chạy; đối tượng + tần suất do trình duyệt áp,
 * popup ưu tiên cao nhất còn hợp lệ thắng, mỗi lượt xem tối đa 1 popup.
 */
@Table("site_popup")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class SitePopup implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;

    private @Nullable String title;

    private @Nullable String body;

    private @Nullable String imageKey;

    private @Nullable String ctaLabel;

    private @Nullable String linkUrl;

    private PopupAudience audience;

    private PopupFrequency frequency;

    private int delaySeconds;

    private int priority;

    private boolean active;

    private @Nullable Instant startsAt;

    private @Nullable Instant endsAt;

    private @Nullable UUID updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static SitePopup create(
        String name,
        @Nullable String title,
        @Nullable String body,
        @Nullable String imageKey,
        @Nullable String ctaLabel,
        @Nullable String linkUrl,
        PopupAudience audience,
        PopupFrequency frequency,
        int delaySeconds,
        int priority,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        UUID adminId) {
        var now = Instant.now();
        var popup = new SitePopup(
            UUID.randomUUID(),
            name,
            null,
            null,
            null,
            null,
            null,
            audience,
            frequency,
            0,
            0,
            false,
            null,
            null,
            adminId,
            now,
            now,
            true);
        popup.update(name,
            title,
            body,
            imageKey,
            ctaLabel,
            linkUrl,
            audience,
            frequency,
            delaySeconds,
            priority,
            active,
            startsAt,
            endsAt,
            adminId);
        return popup;
    }

    public void update(
        String name,
        @Nullable String title,
        @Nullable String body,
        @Nullable String imageKey,
        @Nullable String ctaLabel,
        @Nullable String linkUrl,
        PopupAudience audience,
        PopupFrequency frequency,
        int delaySeconds,
        int priority,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        UUID adminId) {
        ContentFields.requireValidWindow(startsAt, endsAt);

        var cleanTitle = ContentFields.optional(title);
        var cleanImageKey = ContentFields.optional(imageKey);
        if (cleanTitle == null && cleanImageKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Popup needs an image or a title");
        }

        this.name = name.trim();
        this.title = cleanTitle;
        this.body = ContentFields.optional(body);
        this.imageKey = cleanImageKey;
        this.ctaLabel = ContentFields.optional(ctaLabel);
        this.linkUrl = ContentFields.optional(linkUrl);
        this.audience = audience;
        this.frequency = frequency;
        this.delaySeconds = delaySeconds;
        this.priority = priority;
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
