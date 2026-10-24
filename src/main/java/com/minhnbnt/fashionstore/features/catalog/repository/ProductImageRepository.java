package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductImage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends CrudRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);

    Optional<ProductImage> findByProductIdAndId(UUID productId, UUID id);

    void deleteByProductId(UUID productId);
}
