package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shops;

    private final ShopMemberRepository members;

    private final ShopAccessChecker access;

    @Transactional
    public ShopResponse register(@NonNull UUID ownerId, @NonNull CreateShopRequest request) {
        var shop = Shop.create(
            request.name(),
            request.slug(),
            descriptionOf(request.description()),
            request.logoKey(),
            request.bannerKey()
        );
        try {
            shops.save(shop);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }
        members.add(ShopMember.owner(shop.getId(), ownerId));
        return toResponse(shop);
    }

    @Transactional
    public ShopResponse approve(@NonNull UUID adminId, @NonNull UUID id) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.approve(adminId);
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID id) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.reject();
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse update(@NonNull UUID userId, @NonNull UUID id, @NonNull UpdateShopRequest request) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        access.requireShopRole(userId, id, MemberRole.OWNER, MemberRole.STAFF);
        shop.updateDetails(request.name(), request.description(), request.logoKey(), request.bannerKey());
        return toResponse(shops.save(shop));
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> getMine(@NonNull UUID userId) {
        var ids = members.findShopIdsByUser(userId);
        return StreamSupport.stream(shops.findAllById(ids).spliterator(), false)
            .sorted(Comparator.comparing(Shop::getCreatedAt).reversed())
            .map(ShopService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ShopResponse getBySlug(@NonNull String slug) {

        var shop = shops.findBySlug(slug).orElse(null);
        if (shop == null || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        return toResponse(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> listByStatus(@NonNull ShopStatus status) {
        return shops.findByStatusOrderByCreatedAtDesc(status).stream()
            .map(ShopService::toResponse)
            .toList();
    }

    private static String descriptionOf(@Nullable String description) {
        if (description == null) {
            return "";
        }
        return description;
    }

    private static ShopResponse toResponse(Shop shop) {
        return new ShopResponse(
            shop.getId(),
            shop.getName(),
            shop.getSlug(),
            shop.getDescription(),
            shop.getLogoKey(),
            shop.getBannerKey(),
            shop.getStatus().name(),
            shop.getCreatedAt()
        );
    }
}
