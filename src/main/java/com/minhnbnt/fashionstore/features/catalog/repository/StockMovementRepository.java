package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.StockMovement;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends CrudRepository<StockMovement, UUID> {

    @Query("SELECT * FROM stock_movement WHERE variant_id = :variantId ORDER BY created_at DESC")
    List<StockMovement> findByVariantId(UUID variantId);

    @Query("SELECT * FROM stock_movement WHERE shop_id = :shopId ORDER BY created_at DESC LIMIT :limit")
    List<StockMovement> findByShopId(UUID shopId, int limit);
}
