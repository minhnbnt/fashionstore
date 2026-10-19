package com.minhnbnt.fashionstore.features.content.repository;

import com.minhnbnt.fashionstore.model.FaqItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Thứ tự nhóm theo khai báo enum {@code FaqCategory}, không theo chữ cái, nên sắp xếp ở service.
 */
public interface FaqItemRepository extends CrudRepository<FaqItem, UUID> {

    @Query("SELECT * FROM faq_item WHERE published")
    List<FaqItem> findPublished();
}
