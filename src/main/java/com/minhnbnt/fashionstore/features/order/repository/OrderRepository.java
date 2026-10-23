package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.Order;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends CrudRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    /**
     * SELECT ... FOR UPDATE: shop chuyển trạng thái và admin hủy can thiệp đi qua đây để không ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(UUID id);

    long countByBuyerId(UUID buyerId);

    long countByShopId(UUID shopId);
}
