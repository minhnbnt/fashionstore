package com.minhnbnt.fashionstore.model;

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
public class ShopInvitation implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private String email;

    private UUID roleId;

    private String token;

    private Instant expiresAt;

    private @Nullable Instant acceptedAt;

    private UUID createdBy;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ShopInvitation(
        UUID id,
        UUID shopId,
        String email,
        UUID roleId,
        String token,
        Instant expiresAt,
        @Nullable Instant acceptedAt,
        UUID createdBy,
        Instant createdAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.email = email;
        this.roleId = roleId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ShopInvitation create(UUID shopId, String email, UUID roleId, UUID createdBy) {
        var now = Instant.now();
        var inv = new ShopInvitation(
            UUID.randomUUID(),
            shopId,
            email,
            roleId,
            UUID.randomUUID().toString(),
            now.plus(48, ChronoUnit.HOURS),
            null,
            createdBy,
            now
        );
        inv.newRecord = true;
        return inv;
    }

    public void accept() {
        this.acceptedAt = Instant.now();
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
