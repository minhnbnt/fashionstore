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

@Table("order_status_history")
@Getter
public class OrderStatusHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID orderId;

    private @Nullable String fromStatus;

    private String toStatus;

    private @Nullable UUID changedBy;

    private String note;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public OrderStatusHistory(
        UUID id,
        UUID orderId,
        @Nullable String fromStatus,
        String toStatus,
        @Nullable UUID changedBy,
        String note,
        Instant createdAt
    ) {
        this.id = id;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static OrderStatusHistory record(
        UUID orderId,
        @Nullable OrderStatus from,
        OrderStatus to,
        @Nullable UUID changedBy
    ) {
        var history = new OrderStatusHistory(
            UUID.randomUUID(),
            orderId,
            from == null ? null : from.name(),
            to.name(),
            changedBy,
            "",
            Instant.now()
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
