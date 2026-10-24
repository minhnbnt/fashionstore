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

@Table("stock_movement")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static StockMovement record(
        UUID variantId, UUID shopId, int delta, String reason, @Nullable UUID referenceId) {
        return new StockMovement(UUID.randomUUID(), variantId, shopId, delta, reason, referenceId, Instant.now(), true);
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
