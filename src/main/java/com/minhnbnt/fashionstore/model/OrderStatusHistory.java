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

@Table("order_status_history")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static OrderStatusHistory record(
        UUID orderId, @Nullable OrderStatus from, OrderStatus to, @Nullable UUID changedBy) {
        return record(orderId, from, to, changedBy, "");
    }

    public static OrderStatusHistory record(
        UUID orderId,
        @Nullable OrderStatus from,
        OrderStatus to,
        @Nullable UUID changedBy,
        String note
    ) {
        String fromName = null;
        if (from != null) {
            fromName = from.name();
        }

        return new OrderStatusHistory(
            UUID.randomUUID(),
            orderId,
            fromName,
            to.name(),
            changedBy,
            note,
            Instant.now(),
            true
        );
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
