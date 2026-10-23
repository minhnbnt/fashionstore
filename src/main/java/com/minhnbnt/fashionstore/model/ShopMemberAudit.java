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

@Table("shop_member_audit")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopMemberAudit implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private UUID memberId;

    private String action;

    private @Nullable String targetType;

    private @Nullable UUID targetId;

    private @Nullable String detail;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ShopMemberAudit record(
        UUID shopId,
        UUID memberId,
        String action,
        @Nullable String targetType,
        @Nullable UUID targetId,
        @Nullable String detail) {
        return new ShopMemberAudit(
            UUID.randomUUID(), shopId, memberId, action, targetType, targetId, detail, Instant.now(), true);
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
