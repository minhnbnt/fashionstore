package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.Review;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends CrudRepository<Review, UUID> {

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<Review> findByProductIdIn(Collection<UUID> productIds);

    Optional<Review> findByProductIdAndBuyerId(UUID productId, UUID buyerId);

    @Query("""
        SELECT r.* FROM review r
        JOIN product p ON p.id = r.product_id
        WHERE p.shop_id = :shopId
        ORDER BY r.created_at DESC
        """)
    List<Review> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    @Query("""
        SELECT r.* FROM review r
        JOIN product p ON p.id = r.product_id
        WHERE p.shop_id = :shopId AND r.seller_reply IS NULL
        ORDER BY r.created_at DESC
        """)
    List<Review> findUnrepliedByShopIdOrderByCreatedAtDesc(UUID shopId);
}
