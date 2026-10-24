package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopInvitation;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopInvitationRepository extends CrudRepository<ShopInvitation, UUID> {

    List<ShopInvitation> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ShopInvitation> findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID shopId);

    Optional<ShopInvitation> findByShopIdAndId(UUID shopId, UUID id);

    Optional<ShopInvitation> findByToken(String token);

    /**
     * Chiếm lời mời theo kiểu atomic: chỉ một request accept thắng, và accept thua revoke.
     * Trả về số dòng đã cập nhật (0 = đã được accept hoặc đã bị thu hồi).
     */
    @Modifying
    @Query("""
        UPDATE shop_invitation SET accepted_at = now()
        WHERE id = :id AND accepted_at IS NULL AND revoked_at IS NULL
        """)
    int claimPending(@Param("id") UUID id);
}
