package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.OrderItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends CrudRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    @Query("""
        SELECT COUNT(*) > 0 FROM order_item oi
        JOIN orders o ON o.id = oi.order_id
        WHERE oi.product_id = :productId
          AND o.status IN ('PENDING', 'CONFIRMED', 'SHIPPING')
    """)
    boolean hasActiveOrdersForProduct(UUID productId);

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0) FROM order_item oi
        JOIN orders o ON o.id = oi.order_id
        WHERE oi.product_id = :productId
          AND o.status IN ('CONFIRMED', 'SHIPPING', 'DELIVERED')
    """)
    int countSoldQuantityByProductId(UUID productId);

    public record TopProductRow(UUID productId, String productName, long totalQuantity, long totalRevenue) {
    }

    @Query("""
        SELECT oi.product_id AS product_id,
               MIN(oi.product_name) AS product_name,
               SUM(oi.quantity)::bigint AS total_quantity,
               SUM(oi.line_total)::bigint AS total_revenue
        FROM order_item oi
        JOIN orders o ON o.id = oi.order_id
        WHERE o.shop_id = :shopId
          AND o.status = 'DELIVERED'
          AND oi.product_id IS NOT NULL
        GROUP BY oi.product_id
        ORDER BY total_revenue DESC
        LIMIT :limit
    """)
    List<TopProductRow> findTopProducts(UUID shopId, int limit);
}
