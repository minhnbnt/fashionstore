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

@Table("user_status_history")
@Getter
public class UserStatusHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private @Nullable String fromStatus;

    private String toStatus;

    private @Nullable UUID changedBy;

    private String reason;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public UserStatusHistory(
        UUID id,
        UUID userId,
        @Nullable String fromStatus,
        String toStatus,
        @Nullable UUID changedBy,
        String reason,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static UserStatusHistory record(
        UUID userId,
        AccountStatus from,
        AccountStatus to,
        UUID changedBy,
        String reason
    ) {
        var history = new UserStatusHistory(
            UUID.randomUUID(), userId, from.name(), to.name(), changedBy, reason, Instant.now()
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
