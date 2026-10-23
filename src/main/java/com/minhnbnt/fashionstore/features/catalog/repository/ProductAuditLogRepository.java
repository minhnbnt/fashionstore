package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductAuditLog;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAuditLogRepository extends CrudRepository<ProductAuditLog, UUID> {

    List<ProductAuditLog> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<ProductAuditLog> findByShopIdOrderByCreatedAtDesc(UUID shopId);
}
