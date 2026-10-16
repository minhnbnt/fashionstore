package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductVariant;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends CrudRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdOrderByCreatedAtAsc(UUID productId);

    List<ProductVariant> findByShopId(UUID shopId);

    Optional<ProductVariant> findByShopIdAndId(UUID shopId, UUID id);
}
