package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopInvitation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopInvitationRepository extends CrudRepository<ShopInvitation, UUID> {

    List<ShopInvitation> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ShopInvitation> findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID shopId);

    Optional<ShopInvitation> findByShopIdAndId(UUID shopId, UUID id);

    Optional<ShopInvitation> findByToken(String token);
}
