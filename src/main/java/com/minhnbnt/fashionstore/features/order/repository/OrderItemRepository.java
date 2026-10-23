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
}
