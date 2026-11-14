package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
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

@Table("review")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class Review implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private UUID buyerId;

    private @Nullable UUID orderId;

    private String buyerDisplayName;

    private @Nullable String variantLabel;

    private int stars;

    private @Nullable String comment;

    private String[] imageKeys;

    private @Nullable String videoKey;

    private String[] tags;

    private boolean isAnonymous;

    private int helpfulCount;

    private @Nullable String sellerReply;

    private @Nullable Instant repliedAt;

    private boolean isReported;

    private @Nullable String reportReason;

    private @Nullable Instant editedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static Review create(
        UUID productId,
        UUID buyerId,
        @Nullable UUID orderId,
        String buyerDisplayName,
        @Nullable String variantLabel,
        int stars,
        String comment,
        String[] imageKeys,
        @Nullable String videoKey,
        String[] tags,
        boolean isAnonymous
    ) {
        var now = Instant.now();
        return new Review(
            UUID.randomUUID(),
            productId,
            buyerId,
            orderId,
            buyerDisplayName,
            variantLabel,
            stars,
            comment,
            imageKeys,
            videoKey,
            tags,
            isAnonymous,
            0,
            null,
            null,
            false,
            null,
            null,
            now,
            now,
            true
        );
    }

    public void update(
        int stars,
        String comment,
        String[] imageKeys,
        @Nullable String videoKey,
        String[] tags,
        boolean isAnonymous
    ) {
        if (this.editedAt != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review has already been edited once");
        }
        var now = Instant.now();
        this.stars = stars;
        this.comment = comment;
        this.imageKeys = imageKeys;
        this.videoKey = videoKey;
        this.tags = tags;
        this.isAnonymous = isAnonymous;
        this.editedAt = now;
        this.updatedAt = now;
    }

    public void reply(String reply) {
        if (sellerReply != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review has already been replied to");
        }
        this.sellerReply = reply;
        this.repliedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void report(String reason) {
        this.isReported = true;
        this.reportReason = reason;
        this.updatedAt = Instant.now();
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
