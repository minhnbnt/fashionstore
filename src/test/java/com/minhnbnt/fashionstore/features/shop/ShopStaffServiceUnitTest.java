package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopInvitation;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopStaffServiceUnitTest {

    @Mock
    private ShopRoleRepository roles;

    @Mock
    private ShopMemberRepository members;

    @Mock
    private ShopInvitationRepository invitations;

    @Mock
    private ShopMemberAuditRepository audits;

    @Mock
    private ShopAccessChecker access;

    @InjectMocks
    private ShopStaffService staffService;

    @Test
    void listRolesInitializesDefaultsWhenEmpty() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(roles.findByShopIdOrderByCreatedAtAsc(shopId)).thenReturn(List.of());

        var result = staffService.listRoles(userId, shopId);

        verify(access).requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
        assertThat(result).isNotNull();
    }

    @Test
    void createRoleSavesCustomRoleAndAudit() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var request = new CreateShopRoleRequest("Warehouse Lead", List.of("ORDER_READ", "INVENTORY_WRITE"));

        when(roles.findByShopIdAndName(shopId, "Warehouse Lead")).thenReturn(Optional.empty());

        var response = staffService.createRole(userId, shopId, request);

        assertThat(response.name()).isEqualTo("Warehouse Lead");
        assertThat(response.isSystem()).isFalse();
        verify(roles).save(any());
        verify(audits).save(any());
    }

    @Test
    void createRoleThrowsConflictWhenRoleNameExists() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var request = new CreateShopRoleRequest("Existing Role", List.of("ORDER_READ"));
        var existing = ShopRole.create(shopId, "Existing Role", new String[]{"ORDER_READ"}, false);

        when(roles.findByShopIdAndName(shopId, "Existing Role")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> staffService.createRole(userId, shopId, request))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void myPermissionsRequiresActiveMember() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(access.permissionsOf(userId, shopId)).thenReturn(Set.of(ShopPermission.ORDER_READ));
        when(access.isOwner(userId, shopId)).thenReturn(false);

        var response = staffService.myPermissions(userId, shopId);

        verify(access).requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
        assertThat(response.permissions()).containsExactly("ORDER_READ");
    }

    @Test
    void myPermissionsForStaffWithNoPermissionsReturnsEmpty() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(access.permissionsOf(userId, shopId)).thenReturn(Set.of());
        when(access.isOwner(userId, shopId)).thenReturn(false);

        var response = staffService.myPermissions(userId, shopId);

        assertThat(response.isOwner()).isFalse();
        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void inviteStaffCreatesInvitationToken() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var role = ShopRole.create(shopId, "Staff", new String[]{"ORDER_READ"}, false);
        var request = new InviteStaffRequest("staff@store.com", roleId);

        when(roles.findByShopIdAndId(shopId, roleId)).thenReturn(Optional.of(role));

        var invitation = staffService.inviteStaff(userId, shopId, request);

        assertThat(invitation.email()).isEqualTo("staff@store.com");
        assertThat(invitation.token()).isNotBlank();
        verify(invitations).save(any());
        verify(audits).save(any());
    }

    @Test
    void acceptInvitationAddsMemberToShop() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), null);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        staffService.acceptInvitation(userId, inv.getToken());

        assertThat(inv.getAcceptedAt()).isNotNull();
        verify(members).add(any(ShopMember.class));
        verify(audits).save(any());
    }
}
