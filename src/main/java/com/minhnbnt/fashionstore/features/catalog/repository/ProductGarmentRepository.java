package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductGarment;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductGarmentRepository extends CrudRepository<ProductGarment, UUID> {

    List<ProductGarment> findByProductIdOrderBySortOrderAsc(UUID productId);

    List<ProductGarment> findByProductIdInOrderBySortOrderAsc(Collection<UUID> productIds);

    void deleteByProductId(UUID productId);
}
