package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("review")
@Getter
public class Review implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private UUID buyerId;

    private String buyerDisplayName;

    private int stars;

    private @Nullable String comment;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    @PersistenceCreator
    public Review(
        UUID id,
        UUID productId,
        UUID buyerId,
        String buyerDisplayName,
        int stars,
        @Nullable String comment,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.productId = productId;
        this.buyerId = buyerId;
        this.buyerDisplayName = buyerDisplayName;
        this.stars = stars;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Review create(
        UUID productId,
        UUID buyerId,
        String buyerDisplayName,
        int stars,
        @Nullable String comment
    ) {
        var now = Instant.now();
        var review = new Review(
            UUID.randomUUID(),
            productId,
            buyerId,
            buyerDisplayName,
            stars,
            comment,
            now,
            now
        );
        review.newRecord = true;
        return review;
    }

    public void update(int stars, @Nullable String comment) {
        this.stars = stars;
        this.comment = comment;
        updatedAt = Instant.now();
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
