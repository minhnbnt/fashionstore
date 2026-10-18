package com.minhnbnt.fashionstore.features.content.repository;

import com.minhnbnt.fashionstore.model.SiteBanner;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sắp xếp nhiều cột viết SQL tay: derived query {@code OrderByAAscBAsc} làm AOT repository sinh code lỗi.
 */
public interface SiteBannerRepository extends CrudRepository<SiteBanner, UUID> {

    @Query("SELECT * FROM site_banner ORDER BY placement, sort_order, created_at")
    List<SiteBanner> findAllForAdmin();

    @Query("SELECT * FROM site_banner WHERE placement = :placement ORDER BY sort_order, created_at")
    List<SiteBanner> findByPlacementForAdmin(String placement);

    /**
     * Banner đang chạy tại {@code now}: bật + trong khung giờ. Cùng luật với {@code ScheduleState.RUNNING}.
     */
    @Query("""
        SELECT * FROM site_banner
        WHERE placement = :placement
          AND active
          AND (starts_at IS NULL OR starts_at <= :now)
          AND (ends_at IS NULL OR ends_at > :now)
        ORDER BY sort_order, created_at
        """)
    List<SiteBanner> findRunning(String placement, Instant now);
}
