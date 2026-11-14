package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.ReviewMedia;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewMediaRepository extends CrudRepository<ReviewMedia, UUID> {

    List<ReviewMedia> findByReviewIdOrderBySortOrderAsc(UUID reviewId);

    List<ReviewMedia> findByReviewIdIn(Collection<UUID> reviewIds);

    void deleteByReviewId(UUID reviewId);
}
