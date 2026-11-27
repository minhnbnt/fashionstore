package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends CrudRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    @Query("SELECT * FROM orders WHERE shop_id = :shopId AND placed_at IS NOT NULL ORDER BY created_at DESC")
    List<Order> findPlacedByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);

    @Query("""
        SELECT *
        FROM orders
        WHERE status = 'AWAITING_PAYMENT'
          AND payment_expires_at IS NOT NULL
          AND payment_expires_at <= :now
        ORDER BY payment_expires_at ASC, id ASC
        """)
    List<Order> findExpiredAwaitingPaymentOrders(Instant now);

    /**
     * SELECT ... FOR UPDATE: shop chuyển trạng thái và admin hủy can thiệp đi qua đây để không ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(UUID id);

    long countByBuyerId(UUID buyerId);

    long countByShopId(UUID shopId);

    @Query("SELECT COUNT(*) FROM orders WHERE shop_id = :shopId AND placed_at IS NOT NULL")
    long countPlacedByShopId(UUID shopId);

    public record RevenueSummaryRow(long totalRevenue, long totalOrders, long avgOrderValue) {
    }

    public record DailyRevenueRow(LocalDate day, long revenue, long orders) {
    }

    @Query("""
        SELECT COALESCE(SUM(net_rev), 0)::bigint AS total_revenue,
               COUNT(*) AS total_orders,
               COALESCE(SUM(net_rev) / NULLIF(COUNT(*), 0), 0)::bigint AS avg_order_value
        FROM (
            SELECT GREATEST(
                       0,
                       o.subtotal - o.discount_amount - COALESCE((
                           SELECT SUM(
                               CASE
                                   WHEN UPPER(COALESCE(rr.resolution, '')) = 'EXCHANGE' THEN 0
                                   WHEN rr.refund_amount IS NOT NULL AND rr.refund_amount > 0 THEN rr.refund_amount
                                   WHEN UPPER(COALESCE(rr.resolution, '')) = 'REFUND' THEN (o.subtotal - o.discount_amount)
                                   ELSE 0
                               END
                           )
                           FROM return_request rr
                           WHERE rr.order_id = o.id
                             AND rr.status = 'COMPLETED'
                       ), 0)
                   )::bigint AS net_rev
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.status = 'DELIVERED'
        ) t
        """)
    RevenueSummaryRow sumDeliveredRevenue(UUID shopId);

    @Query("""
        SELECT (o.created_at AT TIME ZONE 'UTC')::date AS day,
               SUM(
                   GREATEST(
                       0,
                       o.subtotal - o.discount_amount - COALESCE((
                           SELECT SUM(
                               CASE
                                   WHEN UPPER(COALESCE(rr.resolution, '')) = 'EXCHANGE' THEN 0
                                   WHEN rr.refund_amount IS NOT NULL AND rr.refund_amount > 0 THEN rr.refund_amount
                                   WHEN UPPER(COALESCE(rr.resolution, '')) = 'REFUND' THEN (o.subtotal - o.discount_amount)
                                   ELSE 0
                               END
                           )
                           FROM return_request rr
                           WHERE rr.order_id = o.id
                             AND rr.status = 'COMPLETED'
                       ), 0)
                   )
               )::bigint AS revenue,
               COUNT(*) AS orders
        FROM orders o
        WHERE o.shop_id = :shopId
          AND o.status = 'DELIVERED'
        GROUP BY day
        ORDER BY day DESC
        """)
    List<DailyRevenueRow> sumDailyRevenue(UUID shopId);

    @Query("""
        SELECT o.*
        FROM orders o
        WHERE o.status = 'DELIVERED'
          AND o.received_at IS NULL
          AND o.delivered_at IS NOT NULL
          AND o.delivered_at <= :cutoff
          AND NOT EXISTS (
              SELECT 1
              FROM return_request rr
              WHERE rr.order_id = o.id
                AND rr.status IN ('REQUESTED', 'APPROVED', 'COMPLETED')
          )
        ORDER BY o.delivered_at ASC, o.id
        """)
    List<Order> findDeliveredReadyForAutoComplete(Instant cutoff);
}
