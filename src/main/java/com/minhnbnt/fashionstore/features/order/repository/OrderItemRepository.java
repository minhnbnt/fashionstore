package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.OrderItem;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends CrudRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);
}
