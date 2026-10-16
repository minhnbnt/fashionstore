package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopMember;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ShopMemberRepository {

    private final JdbcClient jdbc;

    public void add(ShopMember member) {
        jdbc.sql("INSERT INTO shop_member (shop_id, user_id, role, joined_at) VALUES (:shop, :user, :role, :at)")
            .param("shop", member.getShopId())
            .param("user", member.getUserId())
            .param("role", member.getRole().name())
            .param("at", member.getJoinedAt().atOffset(ZoneOffset.UTC))
            .update();
    }

    public Optional<MemberRole> findRole(UUID shopId, UUID userId) {
        return jdbc.sql("SELECT role FROM shop_member WHERE shop_id = :shop AND user_id = :user")
            .param("shop", shopId)
            .param("user", userId)
            .query((rs, rowNum) -> MemberRole.valueOf(rs.getString("role")))
            .optional();
    }

    public List<UUID> findShopIdsByUser(UUID userId) {
        return jdbc.sql("SELECT shop_id FROM shop_member WHERE user_id = :user ORDER BY joined_at")
            .param("user", userId)
            .query((rs, rowNum) -> (UUID) rs.getObject("shop_id"))
            .list();
    }

    public Optional<UUID> findOwner(UUID shopId) {
        return jdbc.sql("SELECT user_id FROM shop_member WHERE shop_id = :shop AND role = 'OWNER'")
            .param("shop", shopId)
            .query((rs, rowNum) -> (UUID) rs.getObject("user_id"))
            .optional();
    }
}
