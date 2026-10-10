package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("shop_member")
@Getter
@RequiredArgsConstructor
public class ShopMember {

    private final UUID shopId;

    private final UUID userId;

    private final MemberRole role;

    private final Instant joinedAt;

    public static ShopMember owner(UUID shopId, UUID userId) {
        return new ShopMember(shopId, userId, MemberRole.OWNER, Instant.now());
    }
}
