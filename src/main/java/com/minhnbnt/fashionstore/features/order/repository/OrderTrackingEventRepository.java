package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.OrderTrackingEvent;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTrackingEventRepository extends CrudRepository<OrderTrackingEvent, UUID> {

    List<OrderTrackingEvent> findByOrderIdOrderByEventTimeDescCreatedAtDesc(UUID orderId);
}

