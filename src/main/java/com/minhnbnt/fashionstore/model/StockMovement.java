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

@Table("stock_movement")
@Getter
public class StockMovement implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID variantId;

    private UUID shopId;

    private int delta;

    private String reason;

    private @Nullable UUID referenceId;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public StockMovement(
        UUID id,
        UUID variantId,
        UUID shopId,
        int delta,
        String reason,
        @Nullable UUID referenceId,
        Instant createdAt
    ) {
        this.id = id;
        this.variantId = variantId;
        this.shopId = shopId;
        this.delta = delta;
        this.reason = reason;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static StockMovement record(
        UUID variantId,
        UUID shopId,
        int delta,
        String reason,
        @Nullable UUID referenceId
    ) {
        var movement = new StockMovement(
            UUID.randomUUID(),
            variantId,
            shopId,
            delta,
            reason,
            referenceId,
            Instant.now()
        );
        movement.newRecord = true;
        return movement;
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
