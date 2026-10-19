package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.ProductModerationHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ProductModerationHistoryRepository extends CrudRepository<ProductModerationHistory, UUID> {
}
