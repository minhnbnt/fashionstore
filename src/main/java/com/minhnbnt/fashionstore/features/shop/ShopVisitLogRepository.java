package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopVisitLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ShopVisitLogRepository extends CrudRepository<ShopVisitLog, UUID> {

    long countByShopIdAndVisitedAtAfter(UUID shopId, Instant after);
}
