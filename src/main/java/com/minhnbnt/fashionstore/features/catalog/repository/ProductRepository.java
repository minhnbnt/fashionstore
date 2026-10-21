package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends CrudRepository<Product, UUID> {

    Optional<Product> findByShopIdAndId(UUID shopId, UUID id);

    Optional<Product> findByShopIdAndSlug(UUID shopId, String slug);

    /**
     * SELECT ... FOR UPDATE: admin gỡ/khôi phục đi qua đây để 2 admin bấm cùng lúc không ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Product> findLockedById(UUID id);

    long countByShopId(UUID shopId);

    List<Product> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<Product> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, ProductStatus status);
}
