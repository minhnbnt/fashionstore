package com.minhnbnt.fashionstore.security;

import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopPermission;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAccessCheckerTest {

    @Mock
    private ShopMemberRepository members;

    @Mock
    private ShopRepository shops;

    @InjectMocks
    private ShopAccessChecker checker;

    private static ShopMemberRepository.MemberAccess access(
        MemberRole role,
        @Nullable UUID roleId,
        String status,
        String... permissions
    ) {
        return new ShopMemberRepository.MemberAccess(role, roleId, status, permissions);
    }

    private static void assertForbidden(Runnable call, String reason) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getReason()).isEqualTo(reason);
            });
    }

    @Test
    void ownerHasAllPermissions() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(members.findAccess(shopId, userId)).thenReturn(Optional.of(access(MemberRole.OWNER, null, "ACTIVE")));

        var granted = checker.permissionsOf(userId, shopId);

        assertThat(granted).contains(ShopPermission.REVENUE_WITHDRAW);
        assertThat(granted).hasSize(ShopPermission.values().length);
        assertThat(checker.isOwner(userId, shopId)).isTrue();
    }

    @Test
    void staffUsesRolePermissions() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();

        when(members.findAccess(shopId, userId))
            .thenReturn(Optional.of(access(MemberRole.STAFF, roleId, "ACTIVE", "ORDER_READ")));

        checker.requireShopPermission(userId, shopId, ShopPermission.ORDER_READ);

        assertForbidden(
            () -> checker.requireShopPermission(userId, shopId, ShopPermission.REVENUE_WITHDRAW),
            "Insufficient shop permission"
        );
    }

    @Test
    void staffWithoutRoleHasNoPermission() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(members.findAccess(shopId, userId)).thenReturn(Optional.of(access(MemberRole.STAFF, null, "ACTIVE")));

        assertThat(checker.permissionsOf(userId, shopId)).isEmpty();
        assertForbidden(
            () -> checker.requireShopPermission(userId, shopId, ShopPermission.ORDER_READ),
            "Not a member of this shop"
        );
    }

    @Test
    void deactivatedMemberIsDenied() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();

        when(members.findAccess(shopId, userId))
            .thenReturn(Optional.of(access(MemberRole.STAFF, roleId, "DEACTIVATED", "ORDER_READ")));

        assertForbidden(
            () -> checker.requireShopPermission(userId, shopId, ShopPermission.ORDER_READ),
            "Not a member of this shop"
        );
    }

    @Test
    void unknownPermissionStringIsIgnored() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();

        when(members.findAccess(shopId, userId))
            .thenReturn(Optional.of(access(MemberRole.STAFF, roleId, "ACTIVE", "NOT_A_REAL_PERMISSION")));

        assertThat(checker.permissionsOf(userId, shopId)).isEmpty();
    }

    @Test
    void nonMemberIsDenied() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(members.findAccess(shopId, userId)).thenReturn(Optional.empty());

        assertForbidden(
            () -> checker.requireShopPermission(userId, shopId, ShopPermission.ORDER_READ),
            "Not a member of this shop"
        );
    }
}
