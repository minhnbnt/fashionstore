package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.ProductReview;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductReviewRepository extends CrudRepository<ProductReview, UUID> {

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<ProductReview> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ProductReview> findByShopIdAndSellerReplyIsNullOrderByCreatedAtDesc(UUID shopId);

    Optional<ProductReview> findByOrderIdAndProductId(UUID orderId, UUID productId);

    @Query("SELECT COALESCE(AVG(rating), 0) FROM product_review WHERE shop_id = :shopId")
    double avgRatingForShop(UUID shopId);

    @Query("SELECT COUNT(*) FROM product_review WHERE shop_id = :shopId")
    int countReviewsForShop(UUID shopId);
}
