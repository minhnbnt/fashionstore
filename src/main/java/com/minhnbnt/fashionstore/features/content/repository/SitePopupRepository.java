package com.minhnbnt.fashionstore.features.content.repository;

import com.minhnbnt.fashionstore.model.SitePopup;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SitePopupRepository extends CrudRepository<SitePopup, UUID> {

    @Query("SELECT * FROM site_popup ORDER BY priority DESC, updated_at DESC")
    List<SitePopup> findAllForAdmin();

    /**
     * Popup đang chạy, ưu tiên cao trước, hòa thì mới sửa trước: trình duyệt lấy cái đầu tiên còn hợp lệ.
     */
    @Query("""
        SELECT * FROM site_popup
        WHERE active
          AND (starts_at IS NULL OR starts_at <= :now)
          AND (ends_at IS NULL OR ends_at > :now)
        ORDER BY priority DESC, updated_at DESC
        """)
    List<SitePopup> findRunning(Instant now);
}
