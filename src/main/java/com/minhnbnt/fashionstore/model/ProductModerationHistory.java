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

@Table("product_moderation_history")
@Getter
public class ProductModerationHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private String fromStatus;

    private String toStatus;

    private @Nullable String violationType;

    private @Nullable UUID changedBy;

    private String reason;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ProductModerationHistory(
        UUID id,
        UUID productId,
        String fromStatus,
        String toStatus,
        @Nullable String violationType,
        @Nullable UUID changedBy,
        String reason,
        Instant createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.violationType = violationType;
        this.changedBy = changedBy;
        this.reason = reason;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ProductModerationHistory record(
        UUID productId,
        ProductStatus from,
        ProductStatus to,
        @Nullable ProductViolationType violationType,
        UUID changedBy,
        String reason
    ) {
        String violationName = null;
        if (violationType != null) {
            violationName = violationType.name();
        }
        var history = new ProductModerationHistory(
            UUID.randomUUID(), productId, from.name(), to.name(), violationName, changedBy, reason, Instant.now()
        );
        history.newRecord = true;
        return history;
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
