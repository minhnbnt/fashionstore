package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductReport;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductReportRepository extends CrudRepository<ProductReport, UUID> {

    @Query("""
        SELECT EXISTS (
            SELECT 1 FROM product_report
            WHERE product_id = :product AND reporter_id = :reporter AND source = 'USER' AND status = 'OPEN'
        )
        """)
    boolean hasOpenUserReport(@Param("product") UUID productId, @Param("reporter") UUID reporterId);

    /**
     * Cờ hệ thống: đã có cờ đang mở thì bỏ qua (partial unique index), sửa sản phẩm nhiều lần không nhân bản cờ.
     */
    @Modifying
    @Query("""
        INSERT INTO product_report (id, product_id, source, reason, description, status, created_at)
        VALUES (:id, :product, 'SYSTEM', :reason, :description, 'OPEN', now())
        ON CONFLICT DO NOTHING
        """)
    boolean insertSystemFlagIfAbsent(
        @Param("id") UUID id,
        @Param("product") UUID productId,
        @Param("reason") String reason,
        @Param("description") String description
    );

    /**
     * Đóng mọi báo cáo đang mở của sản phẩm (gỡ → ACTION_TAKEN, bác bỏ → DISMISSED). Trả số báo cáo đã đóng.
     */
    @Modifying
    @Query("""
        UPDATE product_report
        SET status = :status, resolved_by = :admin, resolved_at = now(), resolution_note = :note
        WHERE product_id = :product AND status = 'OPEN'
        """)
    int resolveOpen(
        @Param("product") UUID productId,
        @Param("status") String status,
        @Param("admin") UUID adminId,
        @Param("note") @Nullable String note
    );

    @Query("SELECT count(*) FROM product_report WHERE status = 'OPEN'")
    long countOpen();
}
