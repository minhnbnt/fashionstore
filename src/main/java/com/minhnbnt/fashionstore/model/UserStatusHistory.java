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

@Table("user_status_history")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static UserStatusHistory record(
        UUID userId, AccountStatus from, AccountStatus to, UUID changedBy, String reason) {
        return new UserStatusHistory(
            UUID.randomUUID(), userId, from.name(), to.name(), changedBy, reason, Instant.now(), true);
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
