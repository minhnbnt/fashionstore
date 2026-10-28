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

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);

    /**
     * SELECT ... FOR UPDATE: shop chuyển trạng thái và admin hủy can thiệp đi qua đây để không ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(UUID id);

    long countByBuyerId(UUID buyerId);

    long countByShopId(UUID shopId);

    public record RevenueSummaryRow(long totalRevenue, long totalOrders, long avgOrderValue) {
    }

    public record DailyRevenueRow(LocalDate day, long revenue, long orders) {
    }

    @Query("""
        SELECT COALESCE(SUM(subtotal), 0)::bigint AS total_revenue,
               COUNT(*) AS total_orders,
               COALESCE(SUM(subtotal) / NULLIF(COUNT(*), 0), 0)::bigint AS avg_order_value
        FROM orders
        WHERE shop_id = :shopId
          AND status = 'DELIVERED'
        """)
    RevenueSummaryRow sumDeliveredRevenue(UUID shopId);

    @Query("""
        SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
               SUM(subtotal)::bigint AS revenue,
               COUNT(*) AS orders
        FROM orders
        WHERE shop_id = :shopId
          AND status = 'DELIVERED'
        GROUP BY day
        ORDER BY day DESC
        """)
    List<DailyRevenueRow> sumDailyRevenue(UUID shopId);
}
