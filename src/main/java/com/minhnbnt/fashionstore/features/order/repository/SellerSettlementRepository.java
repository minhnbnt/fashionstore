package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerSettlementRepository extends CrudRepository<SellerSettlement, UUID> {

    List<SellerSettlement> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<SellerSettlement> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, SettlementStatus status);

    Optional<SellerSettlement> findByOrderId(UUID orderId);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<SellerSettlement> findLockedByOrderId(UUID orderId);

    @Query("""
        SELECT id
        FROM seller_settlement
        WHERE shop_id = :shopId
          AND status IN ('RELEASED', 'WITHDRAWN')
        FOR UPDATE
        """)
    List<UUID> lockReleasedByShopId(UUID shopId);

    @Query("""
        SELECT COALESCE(SUM(net_amount), 0)
        FROM seller_settlement
        WHERE shop_id = :shopId
          AND status IN ('RELEASED', 'WITHDRAWN')
        """)
    long sumAvailableBalance(UUID shopId);

    @Query("SELECT COALESCE(SUM(net_amount), 0) FROM seller_settlement WHERE shop_id = :shopId AND status = 'HOLDING'")
    long sumHoldingBalance(UUID shopId);
}
