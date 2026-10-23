package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("shop_follower")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopFollower {

    private UUID shopId;

    private UUID userId;

    private Instant createdAt;

    public static ShopFollower of(UUID shopId, UUID userId) {
        return new ShopFollower(shopId, userId, Instant.now());
    }
}
