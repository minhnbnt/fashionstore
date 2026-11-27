package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerWithdrawalRepository extends CrudRepository<SellerWithdrawal, UUID> {

    List<SellerWithdrawal> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<SellerWithdrawal> findLockedById(UUID id);

    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM seller_withdrawal
        WHERE shop_id = :shopId
          AND status IN ('REQUESTED', 'PROCESSING', 'COMPLETED')
        """)
    long sumActiveWithdrawals(UUID shopId);

    @Query("""
        SELECT *
        FROM seller_withdrawal
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<SellerWithdrawal> findAllPaged(int limit, long offset);

    @Query("""
        SELECT *
        FROM seller_withdrawal
        WHERE status = :status
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<SellerWithdrawal> findByStatusPaged(String status, int limit, long offset);

    @Query("SELECT COUNT(*) FROM seller_withdrawal WHERE status = :status")
    long countByStatus(String status);
}
