package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.identity.AccountInfo;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.features.identity.IdentityDirectoryService;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopInvitation;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Mock
    private ShopRepository shops;

    @Mock
    private IdentityDirectoryService identity;

    @Mock
    private NotificationService notifications;

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
    void inviteStaffRejectsUnknownEmail() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var role = ShopRole.create(shopId, "Staff", new String[]{"ORDER_READ"}, false);
        var request = new InviteStaffRequest("missing@store.com", roleId);

        when(roles.findByShopIdAndId(shopId, roleId)).thenReturn(Optional.of(role));
        when(identity.findByEmail("missing@store.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.inviteStaff(ownerId, shopId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.NOT_FOUND,
                "No registered account with this email"
            ));
    }

    @Test
    void inviteStaffCreatesNotification() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inviteeId = UUID.randomUUID();
        var role = ShopRole.create(shopId, "Warehouse", new String[]{"ORDER_READ"}, false);
        var request = new InviteStaffRequest("  Staff@Store.COM ", roleId);

        when(roles.findByShopIdAndId(shopId, roleId)).thenReturn(Optional.of(role));
        when(invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId))
            .thenReturn(List.of());
        when(identity.findByEmail("staff@store.com"))
            .thenReturn(Optional.of(new AccountInfo(inviteeId, "staff@store.com", "Staff")));
        when(members.findMember(shopId, inviteeId)).thenReturn(Optional.empty());
        when(shops.findById(shopId)).thenReturn(Optional.of(ShopTestFixtures.shop("Fashion Shop", "fashion-shop", "Mo ta")));
        when(identity.findById(ownerId))
            .thenReturn(Optional.of(new AccountInfo(ownerId, "owner@store.com", "Owner")));

        var invitation = staffService.inviteStaff(ownerId, shopId, request);

        assertThat(invitation.email()).isEqualTo("staff@store.com");
        assertThat(invitation.inviteeId()).isEqualTo(inviteeId);
        assertThat(invitation.token()).isNotBlank();
        verify(invitations).save(any());
        verify(notifications).notifyInvitation(
            eq(inviteeId),
            eq(invitation.id()),
            eq("Fashion Shop"),
            eq("Warehouse"),
            eq("Owner"),
            eq(invitation.token())
        );
        verify(audits).save(any());
    }

    @Test
    void inviteStaffRejectsExistingMember() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inviteeId = UUID.randomUUID();
        var role = ShopRole.create(shopId, "Staff", new String[]{"ORDER_READ"}, false);
        var request = new InviteStaffRequest("staff@store.com", roleId);

        when(roles.findByShopIdAndId(shopId, roleId)).thenReturn(Optional.of(role));
        when(invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId))
            .thenReturn(List.of());
        when(identity.findByEmail("staff@store.com"))
            .thenReturn(Optional.of(new AccountInfo(inviteeId, "staff@store.com", "Staff")));
        when(members.findMember(shopId, inviteeId))
            .thenReturn(Optional.of(ShopMember.staff(shopId, inviteeId, roleId, ownerId)));

        assertThatThrownBy(() -> staffService.inviteStaff(ownerId, shopId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "This account is already a member of the shop"
            ));
    }

    @Test
    void inviteStaffRejectsDuplicatePendingInvitation() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var role = ShopRole.create(shopId, "Staff", new String[]{"ORDER_READ"}, false);
        var pending = ShopInvitation.create(shopId, "staff@store.com", roleId, ownerId, UUID.randomUUID());
        var request = new InviteStaffRequest("STAFF@store.com", roleId);

        when(roles.findByShopIdAndId(shopId, roleId)).thenReturn(Optional.of(role));
        when(identity.findByEmail("staff@store.com"))
            .thenReturn(Optional.of(new AccountInfo(UUID.randomUUID(), "staff@store.com", "Staff")));
        when(invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId))
            .thenReturn(List.of(pending));

        assertThatThrownBy(() -> staffService.inviteStaff(ownerId, shopId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "A pending invitation already exists for this email"
            ));
        verify(invitations, never()).save(any());
    }

    @Test
    void acceptInvitationRejectsWrongEmail() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inviteeId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, ownerId, inviteeId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> staffService.acceptInvitation(actorId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.FORBIDDEN,
                "Invitation does not belong to this account"
            ));
        verify(members, never()).add(any());
    }

    @Test
    void acceptInvitationMarksNotificationRead() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), userId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(members.findMember(shopId, userId)).thenReturn(Optional.empty());
        when(invitations.claimPending(inv.getId())).thenReturn(1);

        staffService.acceptInvitation(userId, inv.getToken());

        verify(notifications).deleteByRefId(inv.getId());
    }

    @Test
    void acceptInvitationReactivatesDeactivatedMember() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), userId);
        var deactivated = ShopMember.staff(shopId, userId, null, null);
        deactivated.deactivate();

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(members.findMember(shopId, userId)).thenReturn(Optional.of(deactivated));
        when(invitations.claimPending(inv.getId())).thenReturn(1);

        staffService.acceptInvitation(userId, inv.getToken());

        verify(members).updateStatus(shopId, userId, "ACTIVE");
        verify(members).updateRole(shopId, userId, MemberRole.STAFF, roleId);
        verify(members, never()).add(any());
    }

    @Test
    void acceptInvitationRejectsRevoked() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), userId);
        inv.revoke();

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> staffService.acceptInvitation(userId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.GONE,
                "Invitation has been revoked"
            ));
    }

    @Test
    void acceptInvitationAddsMemberToShop() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), userId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(members.findMember(shopId, userId)).thenReturn(Optional.empty());
        when(invitations.claimPending(inv.getId())).thenReturn(1);

        staffService.acceptInvitation(userId, inv.getToken());

        verify(invitations).claimPending(inv.getId());
        verify(members).add(any(ShopMember.class));
        verify(audits).save(any());
    }

    @Test
    void acceptInvitationFallsBackToEmailWhenInviteeIdMissing() {
        var actorId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), null);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(identity.findById(actorId))
            .thenReturn(Optional.of(new AccountInfo(actorId, "STAFF@Store.com", "Staff")));
        when(invitations.claimPending(inv.getId())).thenReturn(1);
        when(members.findMember(shopId, actorId)).thenReturn(Optional.empty());

        staffService.acceptInvitation(actorId, inv.getToken());

        verify(members).add(any(ShopMember.class));
    }

    @Test
    void acceptInvitationRejectsDifferentEmailWhenInviteeIdMissing() {
        var actorId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", roleId, UUID.randomUUID(), null);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(identity.findById(actorId))
            .thenReturn(Optional.of(new AccountInfo(actorId, "other@store.com", "Other")));

        assertThatThrownBy(() -> staffService.acceptInvitation(actorId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.FORBIDDEN,
                "Invitation does not belong to this account"
            ));
        verify(invitations, never()).claimPending(any());
    }

    @Test
    void acceptInvitationRejectsExpired() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var inv = expiredInvitation(shopId, UUID.randomUUID(), UUID.randomUUID());

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> staffService.acceptInvitation(userId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.GONE,
                "Invitation has expired"
            ));
        verify(invitations, never()).claimPending(any());
    }

    @Test
    void acceptInvitationRejectsActiveMemberInsteadOfDemotingOwner() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "owner@store.com", roleId, UUID.randomUUID(), ownerId);
        var owner = ShopMember.owner(shopId, ownerId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(invitations.claimPending(inv.getId())).thenReturn(1);
        when(members.findMember(shopId, ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> staffService.acceptInvitation(ownerId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "This account is already a member of the shop"
            ));
        verify(members, never()).updateStatus(any(), any(), any());
        verify(members, never()).updateRole(any(), any(), any(), any());
    }

    @Test
    void acceptInvitationReturnsConflictWhenClaimLoses() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", UUID.randomUUID(), UUID.randomUUID(), userId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(invitations.claimPending(inv.getId())).thenReturn(0);

        assertThatThrownBy(() -> staffService.acceptInvitation(userId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "Invitation has already been accepted"
            ));
        verify(members, never()).add(any());
        verify(audits, never()).save(any());
    }

    @Test
    void acceptInvitationConvertsDuplicateMemberInsertToConflict() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", UUID.randomUUID(), UUID.randomUUID(), userId);

        when(invitations.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(invitations.claimPending(inv.getId())).thenReturn(1);
        when(members.findMember(shopId, userId)).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate key"))
            .when(members).add(any(ShopMember.class));

        assertThatThrownBy(() -> staffService.acceptInvitation(userId, inv.getToken()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "This account is already a member of the shop"
            ));
    }

    @Test
    void getInvitationDetailReportsStatusPrecedence() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();

        var revokedAndAccepted = ShopInvitation.create(shopId, "staff@store.com", roleId, ownerId, userId);
        revokedAndAccepted.revoke();
        revokedAndAccepted.accept();

        var acceptedAndExpired = expiredInvitation(shopId, roleId, ownerId);
        acceptedAndExpired.accept();

        var expired = expiredInvitation(shopId, roleId, ownerId);
        var pending = ShopInvitation.create(shopId, "staff@store.com", roleId, ownerId, userId);

        when(invitations.findByToken(revokedAndAccepted.getToken())).thenReturn(Optional.of(revokedAndAccepted));
        when(invitations.findByToken(acceptedAndExpired.getToken())).thenReturn(Optional.of(acceptedAndExpired));
        when(invitations.findByToken(expired.getToken())).thenReturn(Optional.of(expired));
        when(invitations.findByToken(pending.getToken())).thenReturn(Optional.of(pending));

        assertThat(staffService.getInvitationDetail(userId, revokedAndAccepted.getToken()).status())
            .isEqualTo("REVOKED");
        assertThat(staffService.getInvitationDetail(userId, acceptedAndExpired.getToken()).status())
            .isEqualTo("ACCEPTED");
        assertThat(staffService.getInvitationDetail(userId, expired.getToken()).status()).isEqualTo("EXPIRED");
        assertThat(staffService.getInvitationDetail(userId, pending.getToken()).status()).isEqualTo("PENDING");
    }

    @Test
    void listInvitationsExcludesExpired() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var pending = ShopInvitation.create(shopId, "staff@store.com", roleId, ownerId, userId);
        var expired = expiredInvitation(shopId, roleId, ownerId);

        when(invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId))
            .thenReturn(List.of(pending, expired));

        var result = staffService.listInvitations(userId, shopId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(pending.getId());
    }

    @Test
    void revokeInvitationRejectsAcceptedInvitation() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", UUID.randomUUID(), ownerId, UUID.randomUUID());
        inv.accept();

        when(invitations.findByShopIdAndId(shopId, inv.getId())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> staffService.revokeInvitation(ownerId, shopId, inv.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "Invitation has already been accepted"
            ));
        verify(audits, never()).save(any());
    }

    @Test
    void revokeInvitationRequiresOwner() {
        var actorId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var invitationId = UUID.randomUUID();

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"))
            .when(access).requireShopRole(actorId, shopId, MemberRole.OWNER);

        assertThatThrownBy(() -> staffService.revokeInvitation(actorId, shopId, invitationId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN, "Forbidden"));
        verify(invitations, never()).findByShopIdAndId(any(), any());
    }

    @Test
    void revokeInvitationIsIdempotent() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var inv = ShopInvitation.create(shopId, "staff@store.com", UUID.randomUUID(), ownerId, UUID.randomUUID());
        inv.revoke();

        when(invitations.findByShopIdAndId(shopId, inv.getId())).thenReturn(Optional.of(inv));

        staffService.revokeInvitation(ownerId, shopId, inv.getId());

        verify(invitations, never()).save(any());
        verify(audits, never()).save(any());
        verify(notifications, never()).deleteByRefId(any());
    }

    @Test
    void deactivateMemberSetsStatus() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var target = ShopMember.staff(shopId, memberId, null, ownerId);

        when(members.findMember(shopId, memberId)).thenReturn(Optional.of(target));

        staffService.removeMember(ownerId, shopId, memberId);

        verify(members).updateStatus(shopId, memberId, "DEACTIVATED");
        var captor = ArgumentCaptor.forClass(ShopMemberAudit.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("DEACTIVATE_MEMBER");
    }

    @Test
    void activateMemberSetsStatus() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var target = ShopMember.staff(shopId, memberId, null, ownerId);

        when(members.findMember(shopId, memberId)).thenReturn(Optional.of(target));

        staffService.updateMemberStatus(ownerId, shopId, memberId, "ACTIVE");

        verify(access).requireShopRole(ownerId, shopId, MemberRole.OWNER);
        verify(members).updateStatus(shopId, memberId, "ACTIVE");
        var captor = ArgumentCaptor.forClass(ShopMemberAudit.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("ACTIVATE_MEMBER");
    }

    @Test
    void cannotDeactivateOwner() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var target = ShopMember.owner(shopId, memberId);

        when(members.findMember(shopId, memberId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> staffService.updateMemberStatus(ownerId, shopId, memberId, "DEACTIVATED"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.CONFLICT,
                "Cannot change status of OWNER"
            ));
        verify(members, never()).updateStatus(any(), any(), any());
        verify(audits, never()).save(any());
    }

    @Test
    void updateMemberStatusRejectsUnknownStatus() {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var memberId = UUID.randomUUID();

        assertThatThrownBy(() -> staffService.updateMemberStatus(ownerId, shopId, memberId, "BANNED"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus(
                (ResponseStatusException) e,
                HttpStatus.BAD_REQUEST,
                "Invalid member status"
            ));
        verify(members, never()).updateStatus(any(), any(), any());
    }

    private static ShopInvitation expiredInvitation(UUID shopId, UUID roleId, UUID ownerId) {
        return new ShopInvitation(
            UUID.randomUUID(),
            shopId,
            "staff@store.com",
            roleId,
            UUID.randomUUID().toString(),
            Instant.now().minusSeconds(60),
            null,
            null,
            null,
            ownerId,
            Instant.now().minusSeconds(3600),
            false);
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus expected, String reason) {
        assertThat(e.getStatusCode()).isEqualTo(expected);
        assertThat(e.getReason()).isEqualTo(reason);
    }
}
