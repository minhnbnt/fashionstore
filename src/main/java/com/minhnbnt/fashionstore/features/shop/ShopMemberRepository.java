package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    public record MemberAccess(MemberRole role, @Nullable UUID roleId, String status, String[] permissions) {
    }

    public Optional<MemberAccess> findAccess(UUID shopId, UUID userId) {
        return jdbc.sql("""
            SELECT m.role, m.role_id, m.status, r.permissions
            FROM shop_member m
            LEFT JOIN shop_role r ON r.id = m.role_id AND r.shop_id = m.shop_id
            WHERE m.shop_id = :shop AND m.user_id = :user
            """)
            .param("shop", shopId)
            .param("user", userId)
            .query((rs, rowNum) -> {
                var permissionsArray = rs.getArray("permissions");
                String[] permissions = new String[0];
                if (permissionsArray != null) {
                    permissions = (String[]) permissionsArray.getArray();
                }
                return new MemberAccess(
                    MemberRole.valueOf(rs.getString("role")),
                    rs.getObject("role_id", UUID.class),
                    rs.getString("status"),
                    permissions
                );
            })
            .optional();
    }

    public Optional<ShopMember> findMember(UUID shopId, UUID userId) {
        return jdbc.sql("SELECT * FROM shop_member WHERE shop_id = :shop AND user_id = :user")
            .param("shop", shopId)
            .param("user", userId)
            .query((rs, rowNum) -> toMember(rs))
            .optional();
    }

    public record ShopMemberView(ShopMember member, String email, String displayName) {
    }

    public List<ShopMemberView> findMemberViews(UUID shopId) {
        return jdbc.sql("""
            SELECT m.*, p.email, p.display_name
            FROM shop_member m
            JOIN user_profile p ON p.id = m.user_id
            WHERE m.shop_id = :shop
            ORDER BY m.joined_at ASC
            """)
            .param("shop", shopId)
            .query((rs, rowNum) -> new ShopMemberView(
                toMember(rs),
                rs.getString("email"),
                rs.getString("display_name")))
            .list();
    }

    public List<UUID> findShopIdsByUser(UUID userId) {
        return jdbc.sql("SELECT shop_id FROM shop_member WHERE user_id = :user AND status = 'ACTIVE' ORDER BY joined_at")
            .param("user", userId)
            .query((rs, rowNum) -> (UUID) rs.getObject("shop_id"))
            .list();
    }

    public record UserShopMembershipRow(
        UUID shopId,
        String shopName,
        String shopSlug,
        @Nullable String logoKey,
        @Nullable String bannerKey,
        ShopStatus shopStatus,
        MemberRole role,
        @Nullable UUID roleId,
        @Nullable String roleName,
        String memberStatus,
        Instant joinedAt,
        String[] permissions
    ) {
    }

    public List<UserShopMembershipRow> findMembershipsByUser(UUID userId) {
        return jdbc.sql("""
            SELECT
                sm.shop_id,
                s.name as shop_name,
                s.slug as shop_slug,
                s.logo_key,
                s.banner_key,
                s.status as shop_status,
                sm.role,
                sm.role_id,
                sr.name as role_name,
                sm.status as member_status,
                sm.joined_at,
                sr.permissions
            FROM shop_member sm
            JOIN shop s ON s.id = sm.shop_id
            LEFT JOIN shop_role sr ON sr.id = sm.role_id AND sr.shop_id = sm.shop_id
            WHERE sm.user_id = :user
            ORDER BY sm.joined_at DESC
            """)
            .param("user", userId)
            .query((rs, rowNum) -> {
                var permissionsArray = rs.getArray("permissions");
                String[] permissions = new String[0];
                if (permissionsArray != null) {
                    permissions = (String[]) permissionsArray.getArray();
                }
                var roleName = rs.getString("role_name");
                var role = MemberRole.valueOf(rs.getString("role"));
                if (role == MemberRole.OWNER && roleName == null) {
                    roleName = "Chủ gian hàng";
                }
                return new UserShopMembershipRow(
                    (UUID) rs.getObject("shop_id"),
                    rs.getString("shop_name"),
                    rs.getString("shop_slug"),
                    rs.getString("logo_key"),
                    rs.getString("banner_key"),
                    ShopStatus.valueOf(rs.getString("shop_status")),
                    role,
                    rs.getObject("role_id", UUID.class),
                    roleName,
                    rs.getString("member_status"),
                    rs.getTimestamp("joined_at").toInstant(),
                    permissions
                );
            })
            .list();
    }

    public Optional<UUID> findOwner(UUID shopId) {
        return jdbc.sql("SELECT user_id FROM shop_member WHERE shop_id = :shop AND role = 'OWNER'")
            .param("shop", shopId)
            .query((rs, rowNum) -> (UUID) rs.getObject("user_id"))
            .optional();
    }

    public void updateRole(UUID shopId, UUID userId, MemberRole role, @Nullable UUID roleId) {
        jdbc.sql("UPDATE shop_member SET role = :role, role_id = :roleId WHERE shop_id = :shop AND user_id = :user")
            .param("role", role.name())
            .param("roleId", roleId)
            .param("shop", shopId)
            .param("user", userId)
            .update();
    }

    public void updateStatus(UUID shopId, UUID userId, String status) {
        jdbc.sql("UPDATE shop_member SET status = :status WHERE shop_id = :shop AND user_id = :user")
            .param("status", status)
            .param("shop", shopId)
            .param("user", userId)
            .update();
    }

    private static ShopMember toMember(ResultSet rs) throws SQLException {
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
    }
}
