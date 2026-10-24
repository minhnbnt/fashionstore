package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.identity.AccountInfo;
import com.minhnbnt.fashionstore.dto.shop.ChangeMemberRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InvitationDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.dto.shop.MyPermissionsResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberAuditResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopRoleResponse;
import com.minhnbnt.fashionstore.dto.shop.StaffInvitationResponse;
import com.minhnbnt.fashionstore.features.identity.IdentityDirectoryService;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopInvitation;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopStaffService {

    private final ShopRoleRepository roles;

    private final ShopMemberRepository members;

    private final ShopInvitationRepository invitations;

    private final ShopMemberAuditRepository audits;

    private final ShopAccessChecker access;

    private final ShopRepository shops;

    private final IdentityDirectoryService identity;

    private final NotificationService notifications;

    @Transactional
    public List<ShopRoleResponse> listRoles(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var existing = roles.findByShopIdOrderByCreatedAtAsc(shopId);
        if (existing.isEmpty()) {
            initDefaultRoles(shopId);
            existing = roles.findByShopIdOrderByCreatedAtAsc(shopId);
        }

        return existing.stream()
            .map(ShopStaffService::toRoleResponse)
            .toList();
    }

    @Transactional
    public ShopRoleResponse createRole(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull CreateShopRoleRequest request
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var existing = roles.findByShopIdAndName(shopId, request.name()).orElse(null);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role with this name already exists");
        }

        var perms = request.permissions().toArray(new String[0]);
        var role = ShopRole.create(shopId, request.name(), perms, false);
        roles.save(role);

        audits.save(ShopMemberAudit.record(shopId, userId, "CREATE_ROLE", "ROLE", role.getId(), role.getName()));

        return toRoleResponse(role);
    }

    @Transactional
    public ShopRoleResponse updateRole(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID roleId,
        @NonNull CreateShopRoleRequest request
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var role = roles.findByShopIdAndId(shopId, roleId).orElse(null);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot edit system roles");
        }

        var perms = request.permissions().toArray(new String[0]);
        role.update(request.name(), perms);
        roles.save(role);

        audits.save(ShopMemberAudit.record(shopId, userId, "UPDATE_ROLE", "ROLE", role.getId(), role.getName()));

        return toRoleResponse(role);
    }

    @Transactional
    public void deleteRole(@NonNull UUID userId, @NonNull UUID shopId, @NonNull UUID roleId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var role = roles.findByShopIdAndId(shopId, roleId).orElse(null);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete system roles");
        }

        // Xóa thông báo trước khi cascade xóa lời mời, tránh người được mời giữ link chết.
        for (var inv : invitations.findByShopIdOrderByCreatedAtDesc(shopId)) {
            if (inv.getRoleId().equals(roleId) && inv.getAcceptedAt() == null && !inv.isRevoked()) {
                notifications.deleteByRefId(inv.getId());
            }
        }

        roles.delete(role);
        audits.save(ShopMemberAudit.record(shopId, userId, "DELETE_ROLE", "ROLE", role.getId(), role.getName()));
    }

    @Transactional(readOnly = true)
    public List<ShopMemberDetailResponse> listMembers(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var memberList = members.findMembersByShop(shopId);
        var result = new ArrayList<ShopMemberDetailResponse>();

        for (var m : memberList) {
            String roleName = m.getRole().name();
            if (m.getRoleId() != null) {
                var r = roles.findById(m.getRoleId()).orElse(null);
                if (r != null) {
                    roleName = r.getName();
                }
            }
            result.add(new ShopMemberDetailResponse(
                m.getShopId(),
                m.getUserId(),
                m.getRole().name(),
                m.getRoleId(),
                roleName,
                m.getInvitedBy(),
                m.getInvitedAt(),
                m.getStatus(),
                m.getJoinedAt()
            ));
        }

        return result;
    }

    @Transactional
    public StaffInvitationResponse inviteStaff(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull InviteStaffRequest request
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var role = roles.findByShopIdAndId(shopId, request.roleId()).orElse(null);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }

        var email = request.email().trim().toLowerCase(Locale.ROOT);

        var account = identity.findByEmail(email).orElse(null);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No registered account with this email");
        }
        if (account.id().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot invite yourself");
        }

        releaseOrRejectPendingInvitation(shopId, email);

        var existingMember = members.findMember(shopId, account.id()).orElse(null);
        if (existingMember != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already a member of the shop");
        }

        var shop = shops.findById(shopId).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found")
        );
        var ownerName = identity.findById(userId).map(AccountInfo::displayName).orElse("Chủ shop");

        var inv = ShopInvitation.create(shopId, email, role.getId(), userId, account.id());
        try {
            invitations.save(inv);
        } catch (DataIntegrityViolationException e) {
            if (!isPendingInvitationConflict(e)) {
                throw e;
            }
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A pending invitation already exists for this email"
            );
        }

        notifications.notifyInvitation(
            account.id(),
            inv.getId(),
            shop.getName(),
            role.getName(),
            ownerName,
            inv.getToken()
        );

        audits.save(ShopMemberAudit.record(
            shopId,
            userId,
            "INVITE_STAFF",
            "INVITATION",
            inv.getId(),
            "Email: " + email
        ));

        return toInvitationResponse(inv);
    }

    @Transactional(readOnly = true)
    public List<StaffInvitationResponse> listInvitations(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        return invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId).stream()
            .filter(invitation -> !invitation.isExpired())
            .map(ShopStaffService::toInvitationResponse)
            .toList();
    }

    @Transactional
    public void revokeInvitation(@NonNull UUID userId, @NonNull UUID shopId, @NonNull UUID invitationId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var inv = invitations.findByShopIdAndId(shopId, invitationId).orElse(null);
        if (inv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        if (inv.isRevoked()) {
            return;
        }
        if (inv.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been accepted");
        }

        inv.revoke();
        invitations.save(inv);
        notifications.deleteByRefId(inv.getId());

        audits.save(ShopMemberAudit.record(
            shopId,
            userId,
            "REVOKE_INVITATION",
            "INVITATION",
            inv.getId(),
            "Email: " + inv.getEmail()
        ));
    }

    @Transactional(readOnly = true)
    public InvitationDetailResponse getInvitationDetail(@NonNull UUID userId, @NonNull String token) {
        var inv = invitations.findByToken(token).orElse(null);
        if (inv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }

        var shop = shops.findById(inv.getShopId()).orElse(null);
        var shopName = "";
        if (shop != null) {
            shopName = shop.getName();
        }

        var role = roles.findById(inv.getRoleId()).orElse(null);
        var roleName = "";
        if (role != null) {
            roleName = role.getName();
        }

        return new InvitationDetailResponse(
            inv.getShopId(),
            shopName,
            roleName,
            inv.getEmail(),
            inv.getExpiresAt(),
            invitationStatus(inv)
        );
    }

    @Transactional
    public void acceptInvitation(@NonNull UUID userId, @NonNull String token) {
        var inv = invitations.findByToken(token).orElse(null);
        if (inv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        if (inv.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation has been revoked");
        }
        if (inv.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been accepted");
        }
        if (inv.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation has expired");
        }

        requireInvitee(inv, userId);

        if (invitations.claimPending(inv.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been accepted");
        }

        var existing = members.findMember(inv.getShopId(), userId).orElse(null);
        if (existing == null) {
            try {
                members.add(ShopMember.staff(inv.getShopId(), userId, inv.getRoleId(), inv.getCreatedBy()));
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This account is already a member of the shop"
                );
            }
        } else if ("DEACTIVATED".equals(existing.getStatus())) {
            members.updateStatus(inv.getShopId(), userId, "ACTIVE");
            members.updateRole(inv.getShopId(), userId, MemberRole.STAFF, inv.getRoleId());
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already a member of the shop");
        }

        notifications.deleteByRefId(inv.getId());

        audits.save(ShopMemberAudit.record(
            inv.getShopId(),
            userId,
            "ACCEPT_INVITATION",
            "MEMBER",
            userId,
            "Joined as staff"
        ));
    }

    @Transactional
    public void changeMemberRole(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID targetMemberId,
        @NonNull ChangeMemberRoleRequest request
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var target = members.findMember(shopId, targetMemberId).orElse(null);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (target.getRole() == MemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify OWNER role");
        }

        var role = roles.findByShopIdAndId(shopId, request.roleId()).orElse(null);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }

        members.updateRole(shopId, targetMemberId, MemberRole.STAFF, request.roleId());
        audits.save(ShopMemberAudit.record(
            shopId,
            userId,
            "CHANGE_MEMBER_ROLE",
            "MEMBER",
            targetMemberId,
            "New role: " + role.getName()
        ));
    }

    @Transactional
    public void removeMember(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID targetMemberId
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        var target = members.findMember(shopId, targetMemberId).orElse(null);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (target.getRole() == MemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove OWNER from shop");
        }

        members.remove(shopId, targetMemberId);
        audits.save(ShopMemberAudit.record(
            shopId,
            userId,
            "REMOVE_MEMBER",
            "MEMBER",
            targetMemberId,
            null
        ));
    }

    @Transactional(readOnly = true)
    public List<ShopMemberAuditResponse> listAuditLogs(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        return audits.findByShopIdOrderByCreatedAtDesc(shopId).stream()
            .map(ShopStaffService::toAuditResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public MyPermissionsResponse myPermissions(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var permissionNames = access.permissionsOf(userId, shopId).stream()
            .map(ShopPermission::name)
            .sorted()
            .toList();

        return new MyPermissionsResponse(access.isOwner(userId, shopId), permissionNames);
    }

    private void releaseOrRejectPendingInvitation(UUID shopId, String email) {
        var pending = invitations.findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(shopId);

        for (var inv : pending) {
            if (!inv.getEmail().equalsIgnoreCase(email)) {
                continue;
            }
            if (!inv.isExpired()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A pending invitation already exists for this email"
                );
            }

            // Lời mời hết hạn vẫn chiếm slot trong unique index, phải thu hồi trước khi mời lại.
            inv.revoke();
            invitations.save(inv);
            notifications.deleteByRefId(inv.getId());
        }
    }

    private void requireInvitee(ShopInvitation inv, UUID userId) {
        if (inv.getInviteeId() != null) {
            if (inv.getInviteeId().equals(userId)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation does not belong to this account");
        }

        var actor = identity.findById(userId).orElse(null);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation does not belong to this account");
        }
        if (!inv.getEmail().equalsIgnoreCase(actor.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation does not belong to this account");
        }
    }

    /**
     * Chỉ coi là trùng lời mời pending khi lỗi là unique violation của index tương ứng;
     * lỗi toàn vẹn khác phải nổi lên nguyên trạng.
     */
    private static boolean isPendingInvitationConflict(DataIntegrityViolationException e) {
        var cause = e.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return true;
        }
        var message = cause.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("uq_shop_invitation_pending");
    }

    private static String invitationStatus(ShopInvitation inv) {
        if (inv.isRevoked()) {
            return "REVOKED";
        }
        if (inv.getAcceptedAt() != null) {
            return "ACCEPTED";
        }
        if (inv.isExpired()) {
            return "EXPIRED";
        }
        return "PENDING";
    }

    private void initDefaultRoles(UUID shopId) {
        roles.save(ShopRole.create(
            shopId,
            "Quản lý (Manager)",
            new String[]{
                "PRODUCT_READ", "PRODUCT_WRITE", "INVENTORY_READ", "INVENTORY_WRITE",
                "ORDER_READ", "ORDER_PROCESS", "ORDER_CANCEL", "RETURN_PROCESS",
                "REVENUE_READ", "REVENUE_WITHDRAW", "REVIEW_READ", "REVIEW_REPLY",
                "CHAT_ACCESS", "DECORATION_WRITE"
            },
            true
        ));
        roles.save(ShopRole.create(
            shopId,
            "Chăm sóc khách hàng (CSKH)",
            new String[]{"ORDER_READ", "REVIEW_READ", "REVIEW_REPLY", "CHAT_ACCESS"},
            true
        ));
        roles.save(ShopRole.create(
            shopId,
            "Kho vận (Warehouse)",
            new String[]{"ORDER_READ", "ORDER_PROCESS", "INVENTORY_READ", "INVENTORY_WRITE"},
            true
        ));
        roles.save(ShopRole.create(
            shopId,
            "Marketing",
            new String[]{"PRODUCT_READ", "DECORATION_WRITE", "REVENUE_READ"},
            true
        ));
    }

    private static ShopRoleResponse toRoleResponse(ShopRole r) {
        return new ShopRoleResponse(
            r.getId(),
            r.getShopId(),
            r.getName(),
            Arrays.asList(r.getPermissions()),
            r.isSystem(),
            r.getCreatedAt()
        );
    }

    private static StaffInvitationResponse toInvitationResponse(ShopInvitation inv) {
        return new StaffInvitationResponse(
            inv.getId(),
            inv.getShopId(),
            inv.getEmail(),
            inv.getRoleId(),
            inv.getInviteeId(),
            inv.getToken(),
            inv.getExpiresAt(),
            inv.getAcceptedAt(),
            inv.getCreatedAt()
        );
    }

    private static ShopMemberAuditResponse toAuditResponse(ShopMemberAudit a) {
        return new ShopMemberAuditResponse(
            a.getId(),
            a.getShopId(),
            a.getMemberId(),
            a.getAction(),
            a.getTargetType(),
            a.getTargetId(),
            a.getDetail(),
            a.getCreatedAt()
        );
    }
}
