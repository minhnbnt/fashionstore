package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.OrderStatusHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends CrudRepository<OrderStatusHistory, UUID> {
}
