package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.model.ReviewReplyTemplate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewReplyTemplateRepository extends CrudRepository<ReviewReplyTemplate, UUID> {

    List<ReviewReplyTemplate> findByShopIdOrderByUpdatedAtDescCreatedAtDesc(UUID shopId);

    long countByShopId(UUID shopId);

    @Query("""
        SELECT COUNT(*) > 0 FROM review_reply_template
        WHERE shop_id = :shopId AND lower(btrim(title)) = lower(:title)
        """)
    boolean existsDuplicateTitle(UUID shopId, String title);

    @Query("""
        SELECT COUNT(*) > 0 FROM review_reply_template
        WHERE shop_id = :shopId AND lower(btrim(title)) = lower(:title) AND id <> :excludeId
        """)
    boolean existsDuplicateTitleExcludingId(UUID shopId, String title, UUID excludeId);
}

