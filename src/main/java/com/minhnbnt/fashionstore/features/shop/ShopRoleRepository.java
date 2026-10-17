package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopRole;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRoleRepository extends CrudRepository<ShopRole, UUID> {

    List<ShopRole> findByShopIdOrderByCreatedAtAsc(UUID shopId);

    Optional<ShopRole> findByShopIdAndName(UUID shopId, String name);

    Optional<ShopRole> findByShopIdAndId(UUID shopId, UUID id);
}
