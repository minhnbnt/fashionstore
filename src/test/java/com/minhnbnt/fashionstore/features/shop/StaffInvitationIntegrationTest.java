package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim luồng mời nhân viên: lời mời đi qua thông báo trong website (không SMTP),
 * thu hồi thì xóa thông báo và lời mời không còn dùng được.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StaffInvitationIntegrationTest {

    @Autowired
    private ShopStaffService staff;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private ShopRoleRepository roles;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ShopInvitationRepository invitationRepository;

    @Autowired
    private ShopMemberRepository memberRepository;

    @Autowired
    private ShopMemberAuditRepository auditRepository;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void inviteExistingAccountCreatesNotification() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");

        var invitation = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(inviteeId), role.getId())
        );

        var page = notifications.findPage(inviteeId, 0, 10);

        assertThat(invitation.inviteeId()).isEqualTo(inviteeId);
        assertThat(page).hasSize(1);
        assertThat(page.getFirst().type()).isEqualTo("SHOP_INVITATION");
        assertThat(page.getFirst().link()).isEqualTo("/invitations/" + invitation.token());
    }

    @Test
    void revokeRemovesNotification() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");
        var invitation = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(inviteeId), role.getId())
        );

        staff.revokeInvitation(ownerId, shopId, invitation.id());

        assertThat(notifications.findPage(inviteeId, 0, 10)).isEmpty();
        assertThatThrownBy(() -> staff.acceptInvitation(inviteeId, invitation.token()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void duplicatePendingInvitationReturnsConflictInsteadOfDatabaseError() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");
        var request = new InviteStaffRequest(emailOf(inviteeId), role.getId());
        staff.inviteStaff(ownerId, shopId, request);

        assertThatThrownBy(() -> staff.inviteStaff(ownerId, shopId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void expiredPendingInvitationDoesNotBlockReinvite() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");
        var staleId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO shop_invitation (id, shop_id, email, role_id, token, expires_at, created_by, created_at)
            VALUES (:id, :shop, :email, :role, :token, now() - interval '1 hour', :createdBy, now() - interval '2 days')
            """)
            .param("id", staleId)
            .param("shop", shopId)
            .param("email", emailOf(inviteeId))
            .param("role", role.getId())
            .param("token", UUID.randomUUID().toString())
            .param("createdBy", ownerId)
            .update();

        var invitation = staff.inviteStaff(ownerId, shopId, new InviteStaffRequest(emailOf(inviteeId), role.getId()));

        var stale = invitationRepository.findByShopIdAndId(shopId, staleId).orElseThrow();

        assertThat(invitation.id()).isNotEqualTo(staleId);
        assertThat(stale.isRevoked()).isTrue();
        assertThat(notifications.findPage(inviteeId, 0, 10)).hasSize(1);
    }

    @Test
    void acceptReactivatesDeactivatedMemberWithoutDuplicateRow() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");
        var invitation = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(inviteeId), role.getId())
        );

        var deactivated = ShopMember.staff(shopId, inviteeId, null, ownerId);
        deactivated.deactivate();
        memberRepository.add(deactivated);

        staff.acceptInvitation(inviteeId, invitation.token());

        assertThat(memberCount(shopId, inviteeId)).isEqualTo(1);
        var member = memberRepository.findMember(shopId, inviteeId).orElseThrow();
        assertThat(member.getStatus()).isEqualTo("ACTIVE");
        assertThat(member.getRoleId()).isEqualTo(role.getId());
    }

    @Test
    void wrongAccountAcceptCreatesNoMemberRow() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var inviteeId = user("invitee");
        var otherId = user("other");
        var invitation = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(inviteeId), role.getId())
        );

        assertThatThrownBy(() -> staff.acceptInvitation(otherId, invitation.token()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(memberCount(shopId, otherId)).isZero();
        assertThat(invitationRepository.findByToken(invitation.token()).orElseThrow().getAcceptedAt()).isNull();
    }

    @Test
    void inviteAcceptRevokeWriteAuditRows() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var acceptedInviteeId = user("accepted");
        var revokedInviteeId = user("revoked");

        var accepted = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(acceptedInviteeId), role.getId())
        );
        staff.acceptInvitation(acceptedInviteeId, accepted.token());

        var revoked = staff.inviteStaff(
            ownerId,
            shopId,
            new InviteStaffRequest(emailOf(revokedInviteeId), role.getId())
        );
        staff.revokeInvitation(ownerId, shopId, revoked.id());

        var actions = auditRepository.findByShopIdOrderByCreatedAtDesc(shopId).stream()
            .map(ShopMemberAudit::getAction)
            .toList();

        assertThat(actions).containsExactlyInAnyOrder(
            "INVITE_STAFF",
            "ACCEPT_INVITATION",
            "INVITE_STAFF",
            "REVOKE_INVITATION"
        );
    }

    private long memberCount(UUID shopId, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM shop_member WHERE shop_id = :shop AND user_id = :user")
            .param("shop", shopId)
            .param("user", userId)
            .query(Long.class)
            .single();
    }

    private UUID user(String prefix) {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest(prefix + "-" + id + "@t.local", prefix, null));
        return id;
    }

    private String emailOf(UUID userId) {
        return users.getById(userId).email();
    }

    private UUID shop(UUID ownerId) {
        return shops.register(ownerId, ShopTestFixtures.createRequest(
            "Invitation Shop",
            "invitation-shop-" + UUID.randomUUID()
        )).id();
    }

    private ShopRole role(UUID shopId) {
        var role = ShopRole.create(shopId, "Invitation Role " + UUID.randomUUID(), new String[]{"ORDER_READ"}, false);
        return roles.save(role);
    }
}
