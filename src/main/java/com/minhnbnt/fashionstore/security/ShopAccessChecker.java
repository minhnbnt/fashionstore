package com.minhnbnt.fashionstore.security;

import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ShopAccessChecker {

    private final ShopMemberRepository members;

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
}
