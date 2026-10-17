package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.ChangeMemberRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberAuditResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopRoleResponse;
import com.minhnbnt.fashionstore.dto.shop.StaffInvitationResponse;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopInvitation;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopStaffService {

    private final ShopRoleRepository roles;

    private final ShopMemberRepository members;

    private final ShopInvitationRepository invitations;

    private final ShopMemberAuditRepository audits;

    private final ShopAccessChecker access;

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

        var inv = ShopInvitation.create(shopId, request.email(), request.roleId(), userId);
        invitations.save(inv);

        audits.save(ShopMemberAudit.record(
            shopId,
            userId,
            "INVITE_STAFF",
            "INVITATION",
            inv.getId(),
            "Email: " + request.email()
        ));

        return toInvitationResponse(inv);
    }

    @Transactional
    public void acceptInvitation(@NonNull UUID userId, @NonNull String token) {
        var inv = invitations.findByToken(token).orElse(null);
        if (inv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        if (inv.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation has expired");
        }
        if (inv.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been accepted");
        }

        inv.accept();
        invitations.save(inv);

        var member = ShopMember.staff(inv.getShopId(), userId, inv.getRoleId(), inv.getCreatedBy());
        members.add(member);

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

    private void initDefaultRoles(UUID shopId) {
        roles.save(ShopRole.create(
            shopId,
            "Quản lý (Manager)",
            new String[]{"PRODUCT_READ", "PRODUCT_WRITE", "ORDER_READ", "ORDER_PROCESS", "INVENTORY_WRITE", "REVENUE_READ"},
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
