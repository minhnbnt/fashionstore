package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopInvitation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopInvitationRepository extends CrudRepository<ShopInvitation, UUID> {

    List<ShopInvitation> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    Optional<ShopInvitation> findByToken(String token);
}
