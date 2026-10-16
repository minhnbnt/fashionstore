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

@Table("shop_status_history")
@Getter
public class ShopStatusHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private @Nullable String fromStatus;

    private String toStatus;

    private @Nullable UUID changedBy;

    private String reason;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ShopStatusHistory(
        UUID id,
        UUID shopId,
        @Nullable String fromStatus,
        String toStatus,
        @Nullable UUID changedBy,
        String reason,
        Instant createdAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ShopStatusHistory record(
        UUID shopId,
        @Nullable ShopStatus from,
        ShopStatus to,
        UUID changedBy,
        String reason
    ) {
        String fromName = null;
        if (from != null) {
            fromName = from.name();
        }
        var history = new ShopStatusHistory(
            UUID.randomUUID(), shopId, fromName, to.name(), changedBy, reason, Instant.now()
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
