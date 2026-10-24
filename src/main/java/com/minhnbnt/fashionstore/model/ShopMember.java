package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("shop_member")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopMember {

    private final UUID shopId;

    private final UUID userId;

    private MemberRole role;

    private @Nullable UUID roleId;

    private @Nullable UUID invitedBy;

    private @Nullable Instant invitedAt;

    private String status;

    private final Instant joinedAt;

    public static ShopMember owner(UUID shopId, UUID userId) {
        return new ShopMember(shopId, userId, MemberRole.OWNER, null, null, null, "ACTIVE", Instant.now());
    }

    public static ShopMember staff(UUID shopId, UUID userId, @Nullable UUID roleId, @Nullable UUID invitedBy) {
        return new ShopMember(
            shopId, userId, MemberRole.STAFF, roleId, invitedBy, Instant.now(), "ACTIVE", Instant.now());
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
