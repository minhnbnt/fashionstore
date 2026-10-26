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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Table("shop_invitation")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopInvitation implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private String email;

    private UUID roleId;

    private String token;

    private Instant expiresAt;

    private @Nullable Instant acceptedAt;

    private @Nullable UUID inviteeId;

    private @Nullable Instant revokedAt;

    private UUID createdBy;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ShopInvitation create(
        UUID shopId,
        String email,
        UUID roleId,
        UUID createdBy,
        @Nullable UUID inviteeId
    ) {
        var now = Instant.now();
        return new ShopInvitation(
            UUID.randomUUID(),
            shopId,
            email,
            roleId,
            UUID.randomUUID().toString(),
            now.plus(48, ChronoUnit.HOURS),
            null,
            inviteeId,
            null,
            createdBy,
            now,
            true);
    }

    public void accept() {
        this.acceptedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean isRevoked() {
        if (revokedAt == null) {
            return false;
        }
        return true;
    }

    public boolean isPending() {
        if (acceptedAt != null) {
            return false;
        }
        if (revokedAt != null) {
            return false;
        }
        return !isExpired();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
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
