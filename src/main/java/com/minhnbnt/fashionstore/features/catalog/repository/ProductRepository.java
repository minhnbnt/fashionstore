package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends CrudRepository<Product, UUID> {

    Optional<Product> findByShopIdAndId(UUID shopId, UUID id);

    List<Product> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<Product> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, ProductStatus status);
}
