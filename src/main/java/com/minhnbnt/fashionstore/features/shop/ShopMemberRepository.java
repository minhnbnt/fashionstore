package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopMember;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ShopMemberRepository {

    private final JdbcClient jdbc;

    public void add(ShopMember member) {
        java.time.OffsetDateTime invitedAt = null;
        if (member.getInvitedAt() != null) {
            invitedAt = member.getInvitedAt().atOffset(ZoneOffset.UTC);
        }

        jdbc.sql("""
            INSERT INTO shop_member (shop_id, user_id, role, role_id, invited_by, invited_at, status, joined_at)
            VALUES (:shop, :user, :role, :roleId, :invitedBy, :invitedAt, :status, :at)
        """)
            .param("shop", member.getShopId())
            .param("user", member.getUserId())
            .param("role", member.getRole().name())
            .param("roleId", member.getRoleId())
            .param("invitedBy", member.getInvitedBy())
            .param("invitedAt", invitedAt)
            .param("status", member.getStatus())
            .param("at", member.getJoinedAt().atOffset(ZoneOffset.UTC))
            .update();
    }

    public Optional<MemberRole> findRole(UUID shopId, UUID userId) {
        return jdbc.sql("SELECT role FROM shop_member WHERE shop_id = :shop AND user_id = :user AND status = 'ACTIVE'")
            .param("shop", shopId)
            .param("user", userId)
            .query((rs, rowNum) -> MemberRole.valueOf(rs.getString("role")))
            .optional();
    }

    public Optional<ShopMember> findMember(UUID shopId, UUID userId) {
        return jdbc.sql("SELECT * FROM shop_member WHERE shop_id = :shop AND user_id = :user")
            .param("shop", shopId)
            .param("user", userId)
            .query((rs, rowNum) -> {
                var invitedTs = rs.getTimestamp("invited_at");
                Instant invitedAt = null;
                if (invitedTs != null) {
                    invitedAt = invitedTs.toInstant();
                }
                var statusStr = rs.getString("status");
                if (statusStr == null) {
                    statusStr = "ACTIVE";
                }
                return new ShopMember(
                    (UUID) rs.getObject("shop_id"),
                    (UUID) rs.getObject("user_id"),
                    MemberRole.valueOf(rs.getString("role")),
                    (UUID) rs.getObject("role_id"),
                    (UUID) rs.getObject("invited_by"),
                    invitedAt,
                    statusStr,
                    rs.getTimestamp("joined_at").toInstant()
                );
            })
            .optional();
    }

    public List<ShopMember> findMembersByShop(UUID shopId) {
        return jdbc.sql("SELECT * FROM shop_member WHERE shop_id = :shop ORDER BY joined_at ASC")
            .param("shop", shopId)
            .query((rs, rowNum) -> {
                var invitedTs = rs.getTimestamp("invited_at");
                Instant invitedAt = null;
                if (invitedTs != null) {
                    invitedAt = invitedTs.toInstant();
                }
                var statusStr = rs.getString("status");
                if (statusStr == null) {
                    statusStr = "ACTIVE";
                }
                return new ShopMember(
                    (UUID) rs.getObject("shop_id"),
                    (UUID) rs.getObject("user_id"),
                    MemberRole.valueOf(rs.getString("role")),
                    (UUID) rs.getObject("role_id"),
                    (UUID) rs.getObject("invited_by"),
                    invitedAt,
                    statusStr,
                    rs.getTimestamp("joined_at").toInstant()
                );
            })
            .list();
    }

    public List<UUID> findShopIdsByUser(UUID userId) {
        return jdbc.sql("SELECT shop_id FROM shop_member WHERE user_id = :user AND status = 'ACTIVE' ORDER BY joined_at")
            .param("user", userId)
            .query((rs, rowNum) -> (UUID) rs.getObject("shop_id"))
            .list();
    }

    public void updateRole(UUID shopId, UUID userId, MemberRole role, @Nullable UUID roleId) {
        jdbc.sql("UPDATE shop_member SET role = :role, role_id = :roleId WHERE shop_id = :shop AND user_id = :user")
            .param("role", role.name())
            .param("roleId", roleId)
            .param("shop", shopId)
            .param("user", userId)
            .update();
    }

    public void remove(UUID shopId, UUID userId) {
        jdbc.sql("DELETE FROM shop_member WHERE shop_id = :shop AND user_id = :user")
            .param("shop", shopId)
            .param("user", userId)
            .update();
    }
}
