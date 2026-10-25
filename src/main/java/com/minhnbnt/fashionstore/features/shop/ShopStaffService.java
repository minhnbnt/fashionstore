package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.identity.AccountInfo;
import com.minhnbnt.fashionstore.dto.shop.ChangeMemberRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InvitationDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.dto.shop.MyPermissionsResponse;
import com.minhnbnt.fashionstore.dto.shop.SellerShopMembershipResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopRoleResponse;
import com.minhnbnt.fashionstore.dto.shop.StaffInvitationResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopActivityResponse;
import com.minhnbnt.fashionstore.features.identity.IdentityDirectoryService;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopInvitation;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopStaffService {

    private final ShopRoleRepository roles;

    private final ShopMemberRepository members;

    private final ShopInvitationRepository invitations;

    private final ShopAuditService audit;

    private final ShopActivityQueryRepository activity;

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

        var permsJoined = String.join(", ", request.permissions());
        var createDetail = "Tạo vai trò: \"" + role.getName() + "\" gồm " + request.permissions().size() + " quyền: [" + permsJoined + "]";
        audit.record(shopId, userId, "CREATE_ROLE", "ROLE", role.getId(), createDetail);

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

        var oldPerms = new HashSet<>(Arrays.asList(role.getPermissions()));
        var newPerms = new HashSet<>(request.permissions());
        var oldName = role.getName();

        var changes = new ArrayList<String>();
        if (!oldName.equals(request.name())) {
            changes.add("Đổi tên: [" + oldName + "] -> [" + request.name() + "]");
        }

        var added = new ArrayList<String>();
        for (var p : request.permissions()) {
            if (!oldPerms.contains(p)) {
                added.add("+" + p);
            }
        }
        if (!added.isEmpty()) {
            changes.add("Thêm quyền: " + String.join(", ", added));
        }

        var removed = new ArrayList<String>();
        for (var p : role.getPermissions()) {
            if (!newPerms.contains(p)) {
                removed.add("-" + p);
            }
        }
        if (!removed.isEmpty()) {
            changes.add("Gỡ quyền: " + String.join(", ", removed));
        }

        if (changes.isEmpty()) {
            changes.add("Không thay đổi quyền");
        }

        var diffStr = String.join(" | ", changes);

        var perms = request.permissions().toArray(new String[0]);
        role.update(request.name(), perms);
        roles.save(role);

        var updateDetail = "Cập nhật vai trò: \"" + role.getName() + "\" | Thay đổi: " + diffStr;
        audit.record(shopId, userId, "UPDATE_ROLE", "ROLE", role.getId(), updateDetail);

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
        audit.record(shopId, userId, "DELETE_ROLE", "ROLE", role.getId(), "Xóa vai trò: \"" + role.getName() + "\"");
    }

    @Transactional(readOnly = true)
    public List<ShopMemberDetailResponse> listMembers(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var memberViews = members.findMemberViews(shopId);
        var result = new ArrayList<ShopMemberDetailResponse>();

        for (var view : memberViews) {
            var m = view.member();
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
                view.email(),
                view.displayName(),
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

        audit.record(
            shopId,
            userId,
            "INVITE_STAFF",
            "INVITATION",
            inv.getId(),
            "Mời " + email + " vào vai trò " + role.getName()
        );

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

        audit.record(
            shopId,
            userId,
            "REVOKE_INVITATION",
            "INVITATION",
            inv.getId(),
            "Thu hồi lời mời của " + inv.getEmail()
        );
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

        var role = roles.findById(inv.getRoleId()).orElse(null);
        var roleName = "Nhân viên";
        if (role != null) {
            roleName = role.getName();
        }

        audit.record(
            inv.getShopId(),
            userId,
            "ACCEPT_INVITATION",
            "MEMBER",
            userId,
            "Chấp nhận lời mời tham gia với vai trò " + roleName
        );
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

        var targetEmail = identity.findById(targetMemberId).map(AccountInfo::email).orElse("thành viên");
        members.updateRole(shopId, targetMemberId, MemberRole.STAFF, request.roleId());
        audit.record(
            shopId,
            userId,
            "CHANGE_MEMBER_ROLE",
            "MEMBER",
            targetMemberId,
            "Đổi vai trò của " + targetEmail + " thành " + role.getName()
        );
    }

    @Transactional
    public void updateMemberStatus(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID targetMemberId,
        @NonNull String status
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        if (!"ACTIVE".equals(status) && !"DEACTIVATED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member status");
        }

        var target = members.findMember(shopId, targetMemberId).orElse(null);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (target.getRole() == MemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot change status of OWNER");
        }

        members.updateStatus(shopId, targetMemberId, status);

        var targetEmail = identity.findById(targetMemberId).map(AccountInfo::email).orElse("thành viên");
        var action = "DEACTIVATE_MEMBER";
        var detail = "Vô hiệu hóa thành viên " + targetEmail;
        if ("ACTIVE".equals(status)) {
            action = "ACTIVATE_MEMBER";
            detail = "Kích hoạt lại thành viên " + targetEmail;
        }
        audit.record(
            shopId,
            userId,
            action,
            "MEMBER",
            targetMemberId,
            detail
        );
    }

    @Transactional
    public void removeMember(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID targetMemberId
    ) {
        updateMemberStatus(userId, shopId, targetMemberId, "DEACTIVATED");
    }

    @Transactional(readOnly = true)
    public PageResponse<ShopActivityResponse> listAuditLogs(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable String action,
        int page,
        int size
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER);

        return activity.page(shopId, action, page, size);
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

    @Transactional(readOnly = true)
    public List<SellerShopMembershipResponse> getMyMemberships(@NonNull UUID userId) {
        var rows = members.findMembershipsByUser(userId);
        var result = new ArrayList<SellerShopMembershipResponse>();

        for (var r : rows) {
            List<String> perms;
            if (r.role() == MemberRole.OWNER) {
                perms = Arrays.stream(ShopPermission.values()).map(Enum::name).toList();
            } else {
                perms = List.of(r.permissions());
            }

            var roleName = r.roleName();
            if (roleName == null) {
                roleName = "Nhân viên";
            }

            result.add(new SellerShopMembershipResponse(
                r.shopId(),
                r.shopName(),
                r.shopSlug(),
                r.logoKey(),
                r.bannerKey(),
                r.shopStatus(),
                r.role(),
                r.roleId(),
                roleName,
                r.memberStatus(),
                r.joinedAt(),
                perms
            ));
        }

        return result;
    }
}
