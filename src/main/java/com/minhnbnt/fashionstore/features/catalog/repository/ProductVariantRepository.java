package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductVariant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends CrudRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdOrderByCreatedAtAsc(UUID productId);

    List<ProductVariant> findByShopId(UUID shopId);

    Optional<ProductVariant> findByShopIdAndId(UUID shopId, UUID id);

    /**
     * Trừ kho có điều kiện: trả false khi tồn kho không đủ, caller phải coi như thất bại.
     */
    @Modifying
    @Query("""
        UPDATE product_variant
        SET stock_quantity = stock_quantity - :qty
        WHERE id = :id AND stock_quantity >= :qty
        """)
    boolean decrementStock(UUID id, int qty);

    @Modifying
    @Query("""
        UPDATE product_variant
        SET stock_quantity = stock_quantity + :qty
        WHERE id = :id
        """)
    boolean incrementStock(UUID id, int qty);
}
