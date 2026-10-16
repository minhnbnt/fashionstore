package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface SellerWithdrawalRepository extends CrudRepository<SellerWithdrawal, UUID> {

    List<SellerWithdrawal> findByShopIdOrderByCreatedAtDesc(UUID shopId);
}
