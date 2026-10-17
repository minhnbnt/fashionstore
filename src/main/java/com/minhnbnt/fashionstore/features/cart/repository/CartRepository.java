package com.minhnbnt.fashionstore.features.cart.repository;

import com.minhnbnt.fashionstore.model.Cart;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends CrudRepository<Cart, UUID> {

    Optional<Cart> findByBuyerId(UUID buyerId);
}
