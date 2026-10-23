package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static ShopStatusHistory record(
        UUID shopId, @Nullable ShopStatus from, ShopStatus to, UUID changedBy, String reason) {
        String fromName = null;
        if (from != null) {
            fromName = from.name();
        }
        return new ShopStatusHistory(
            UUID.randomUUID(), shopId, fromName, to.name(), changedBy, reason, Instant.now(), true);
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
