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

import java.time.Instant;
import java.util.UUID;

@Table("order_tracking_event")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class OrderTrackingEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID orderId;

    private String statusCode;

    private String title;

    private @Nullable String location;

    private @Nullable String description;

    private Instant eventTime;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static OrderTrackingEvent record(
        UUID orderId,
        String statusCode,
        String title,
        @Nullable String location,
        @Nullable String description,
        Instant eventTime,
        @Nullable UUID createdBy
    ) {
        var now = Instant.now();
        return new OrderTrackingEvent(
            UUID.randomUUID(),
            orderId,
            statusCode,
            title,
            location,
            description,
            eventTime,
            createdBy,
            now,
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

