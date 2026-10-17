package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("shop_member")
@Getter
public class ShopMember {

    private final UUID shopId;

    private final UUID userId;

    private MemberRole role;

    private @Nullable UUID roleId;

    private @Nullable UUID invitedBy;

    private @Nullable Instant invitedAt;

    private String status;

    private final Instant joinedAt;

    @PersistenceCreator
    public ShopMember(
        UUID shopId,
        UUID userId,
        MemberRole role,
        @Nullable UUID roleId,
        @Nullable UUID invitedBy,
        @Nullable Instant invitedAt,
        String status,
        Instant joinedAt
    ) {
        this.shopId = shopId;
        this.userId = userId;
        this.role = role;
        this.roleId = roleId;
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public static ShopMember owner(UUID shopId, UUID userId) {
        return new ShopMember(shopId, userId, MemberRole.OWNER, null, null, null, "ACTIVE", Instant.now());
    }

    public static ShopMember staff(UUID shopId, UUID userId, @Nullable UUID roleId, @Nullable UUID invitedBy) {
        return new ShopMember(shopId, userId, MemberRole.STAFF, roleId, invitedBy, Instant.now(), "ACTIVE", Instant.now());
    }

    public void updateRole(MemberRole role, @Nullable UUID roleId) {
        this.role = role;
        this.roleId = roleId;
    }

    public void deactivate() {
        this.status = "DEACTIVATED";
    }

    public void activate() {
        this.status = "ACTIVE";
    }
}
