package com.minhnbnt.fashionstore.security;

import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ShopAccessChecker {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ShopMemberRepository members;

    private final ShopRepository shops;

    public void requireShopRole(UUID userId, UUID shopId, MemberRole... roles) {

        var actual = members.findRole(shopId, userId).orElse(null);
        if (actual == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this shop");
        }

        for (var role : roles) {
            if (actual == role) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient shop role");
    }

    public void requireApprovedShopRole(UUID userId, UUID shopId, MemberRole... roles) {
        requireShopApproved(shopId);

        requireShopRole(userId, shopId, roles);
    }

    public Set<ShopPermission> permissionsOf(UUID userId, UUID shopId) {
        var memberAccess = members.findAccess(shopId, userId).orElse(null);
        if (memberAccess == null) {
            return Set.of();
        }

        if (!ACTIVE_STATUS.equals(memberAccess.status())) {
            return Set.of();
        }

        if (memberAccess.role() == MemberRole.OWNER) {
            return EnumSet.allOf(ShopPermission.class);
        }

        var granted = EnumSet.noneOf(ShopPermission.class);
        for (var raw : memberAccess.permissions()) {
            var permission = ShopPermission.parse(raw);
            if (permission != null) {
                granted.add(permission);
            }
        }

        return granted;
    }

    public boolean isOwner(UUID userId, UUID shopId) {
        var memberAccess = members.findAccess(shopId, userId).orElse(null);
        if (memberAccess == null) {
            return false;
        }

        if (!ACTIVE_STATUS.equals(memberAccess.status())) {
            return false;
        }

        return memberAccess.role() == MemberRole.OWNER;
    }

    public void requireShopPermission(UUID userId, UUID shopId, ShopPermission... required) {
        var granted = permissionsOf(userId, shopId);
        if (granted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this shop");
        }

        for (var permission : required) {
            if (granted.contains(permission)) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient shop permission");
    }

    public void requireApprovedShopPermission(UUID userId, UUID shopId, ShopPermission... required) {
        requireShopApproved(shopId);

        requireShopPermission(userId, shopId, required);
    }

    public void requireShopApproved(UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop is not approved for selling");
        }
    }
}
