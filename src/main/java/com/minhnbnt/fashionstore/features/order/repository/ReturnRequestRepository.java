package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ReturnStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReturnRequestRepository extends CrudRepository<ReturnRequest, UUID> {

    List<ReturnRequest> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ReturnRequest> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, ReturnStatus status);

    List<ReturnRequest> findByOrderId(UUID orderId);

    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<ReturnRequest> findByOrderIdInOrderByCreatedAtDesc(Collection<UUID> orderIds);

    List<ReturnRequest> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @Query("""
        SELECT EXISTS (
            SELECT 1
            FROM return_request rr
            WHERE rr.order_id = :orderId
              AND rr.status IN ('REQUESTED', 'APPROVED', 'COMPLETED')
        )
        """)
    boolean existsBlockingByOrderId(UUID orderId);
}
