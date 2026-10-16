package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopStatusHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ShopStatusHistoryRepository extends CrudRepository<ShopStatusHistory, UUID> {
}
