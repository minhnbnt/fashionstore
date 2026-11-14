package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.Review;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends CrudRepository<Review, UUID> {

    public record ReviewSummaryRow(
        long reviewCount,
        @Nullable Double averageRating,
        long fiveStarCount,
        long fourStarCount,
        long threeStarCount,
        long twoStarCount,
        long oneStarCount,
        long withMediaCount
    ) {
    }

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<Review> findByProductIdIn(Collection<UUID> productIds);

    Optional<Review> findByOrderIdAndProductId(UUID orderId, UUID productId);

    Optional<Review> findByIdAndBuyerId(UUID id, UUID buyerId);

    List<Review> findByBuyerIdOrderByUpdatedAtDescCreatedAtDesc(UUID buyerId);

    @Query("""
        SELECT r.* FROM review r
        WHERE r.product_id = :productId
          AND r.stars BETWEEN :minStars AND :maxStars
          AND (:hasMedia = FALSE OR EXISTS (SELECT 1 FROM review_media rm WHERE rm.review_id = r.id))
        ORDER BY r.helpful_count DESC, r.created_at DESC
        """)
    List<Review> findFilteredOrderByHelpful(UUID productId, int minStars, int maxStars, boolean hasMedia);

    @Query("""
        SELECT r.* FROM review r
        WHERE r.product_id = :productId
          AND r.stars BETWEEN :minStars AND :maxStars
          AND (:hasMedia = FALSE OR EXISTS (SELECT 1 FROM review_media rm WHERE rm.review_id = r.id))
        ORDER BY r.created_at DESC
        """)
    List<Review> findFilteredOrderByNewest(UUID productId, int minStars, int maxStars, boolean hasMedia);

    @Query("""
        SELECT COUNT(*) AS review_count,
               AVG(r.stars)::double precision AS average_rating,
               COUNT(*) FILTER (WHERE r.stars = 5) AS five_star_count,
               COUNT(*) FILTER (WHERE r.stars = 4) AS four_star_count,
               COUNT(*) FILTER (WHERE r.stars = 3) AS three_star_count,
               COUNT(*) FILTER (WHERE r.stars = 2) AS two_star_count,
               COUNT(*) FILTER (WHERE r.stars = 1) AS one_star_count,
               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM review_media rm WHERE rm.review_id = r.id)) AS with_media_count
        FROM review r
        WHERE r.product_id = :productId
        """)
    ReviewSummaryRow summarizeByProductId(UUID productId);

    @Query("""
        SELECT COUNT(*) AS review_count,
               AVG(r.stars)::double precision AS average_rating,
               COUNT(*) FILTER (WHERE r.stars = 5) AS five_star_count,
               COUNT(*) FILTER (WHERE r.stars = 4) AS four_star_count,
               COUNT(*) FILTER (WHERE r.stars = 3) AS three_star_count,
               COUNT(*) FILTER (WHERE r.stars = 2) AS two_star_count,
               COUNT(*) FILTER (WHERE r.stars = 1) AS one_star_count,
               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM review_media rm WHERE rm.review_id = r.id)) AS with_media_count
        FROM review r
        JOIN product p ON p.id = r.product_id
        WHERE p.shop_id = :shopId
        """)
    ReviewSummaryRow summarizeByShopId(UUID shopId);

    @Modifying
    @Query("UPDATE review SET helpful_count = helpful_count + 1 WHERE id = :reviewId")
    void incrementHelpfulCount(UUID reviewId);

    @Modifying
    @Query("UPDATE review SET helpful_count = GREATEST(helpful_count - 1, 0) WHERE id = :reviewId")
    void decrementHelpfulCount(UUID reviewId);

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
