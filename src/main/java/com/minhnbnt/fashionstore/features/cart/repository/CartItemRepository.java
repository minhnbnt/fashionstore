package com.minhnbnt.fashionstore.features.cart.repository;

import com.minhnbnt.fashionstore.model.CartItem;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends CrudRepository<CartItem, UUID> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(UUID cartId);

    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);

    void deleteByCartId(UUID cartId);
}
