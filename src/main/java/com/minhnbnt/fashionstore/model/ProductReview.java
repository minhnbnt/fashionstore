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

@Table("product_review")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductReview implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID orderId;

    private UUID productId;

    private UUID shopId;

    private UUID buyerId;

    private short rating;

    private @Nullable String comment;

    private String[] imageKeys;

    private @Nullable String sellerReply;

    private @Nullable Instant repliedAt;

    private boolean isReported;

    private @Nullable String reportReason;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static ProductReview create(
        UUID orderId,
        UUID productId,
        UUID shopId,
        UUID buyerId,
        short rating,
        @Nullable String comment,
        String[] imageKeys) {
        var now = Instant.now();
        return new ProductReview(
            UUID.randomUUID(),
            orderId,
            productId,
            shopId,
            buyerId,
            rating,
            comment,
            imageKeys,
            null,
            null,
            false,
            null,
            now,
            now,
            true);
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
