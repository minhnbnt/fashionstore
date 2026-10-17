package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopFollower;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ShopFollowerRepository extends Repository<ShopFollower, UUID> {

    @Query("SELECT * FROM shop_follower WHERE shop_id = :shopId AND user_id = :userId")
    Optional<ShopFollower> find(UUID shopId, UUID userId);

    @Modifying
    @Query("INSERT INTO shop_follower (shop_id, user_id, created_at) VALUES (:shopId, :userId, now()) ON CONFLICT DO NOTHING")
    int add(UUID shopId, UUID userId);

    @Modifying
    @Query("DELETE FROM shop_follower WHERE shop_id = :shopId AND user_id = :userId")
    int remove(UUID shopId, UUID userId);

    @Query("SELECT COUNT(*) FROM shop_follower WHERE shop_id = :shopId")
    int countByShop(UUID shopId);
}
