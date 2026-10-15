package com.minhnbnt.fashionstore.features.wishlist.repository;

import com.minhnbnt.fashionstore.model.WishlistItem;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository extends CrudRepository<WishlistItem, UUID> {

    List<WishlistItem> findByBuyerIdOrderByCreatedAtAsc(UUID buyerId);

    Optional<WishlistItem> findByBuyerIdAndProductId(UUID buyerId, UUID productId);
}
