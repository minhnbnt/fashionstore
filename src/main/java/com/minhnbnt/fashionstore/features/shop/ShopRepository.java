package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends CrudRepository<Shop, UUID> {

    Optional<Shop> findBySlug(String slug);

    List<Shop> findByStatusOrderByCreatedAtDesc(ShopStatus status);
}
