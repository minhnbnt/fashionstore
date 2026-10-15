package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.Review;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends CrudRepository<Review, UUID> {

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    Optional<Review> findByProductIdAndBuyerId(UUID productId, UUID buyerId);
}
