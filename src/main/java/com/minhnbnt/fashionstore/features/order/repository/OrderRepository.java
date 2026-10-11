package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.Order;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends CrudRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findByShopIdOrderByCreatedAtDesc(UUID shopId);
}
