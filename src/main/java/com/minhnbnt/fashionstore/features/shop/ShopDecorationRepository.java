package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopDecoration;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopDecorationRepository extends CrudRepository<ShopDecoration, UUID> {

    Optional<ShopDecoration> findByShopId(UUID shopId);
}
