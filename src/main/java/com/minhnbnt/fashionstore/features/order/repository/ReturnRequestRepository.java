package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ReturnStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ReturnRequestRepository extends CrudRepository<ReturnRequest, UUID> {

    List<ReturnRequest> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ReturnRequest> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, ReturnStatus status);

    List<ReturnRequest> findByOrderId(UUID orderId);

    List<ReturnRequest> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);
}
