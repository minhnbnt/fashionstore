package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminShopDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Điều phối nghiệp vụ duyệt/kiểm duyệt shop qua nhiều domain (shop, identity, GoTrue).
 * GoTrue gọi cuối cùng trong transaction: lỗi thì rollback DB, không để 2 bên lệch nhau.
 */
@Service
@RequiredArgsConstructor
public class AdminShopService {

    private final ShopService shops;

    private final UserService users;

    private final CatalogService catalog;

    private final OrderService orders;

    private final GoTrueAdminClient gotrue;

    @Transactional(readOnly = true)
    public PageResponse<AdminShopResponse> search(
        @Nullable ShopStatus status,
        @Nullable String query,
        int page,
        int size
    ) {
        return shops.searchForAdmin(status, query, page, size);
    }

    @Transactional(readOnly = true)
    public AdminShopDetailResponse getDetail(@NonNull UUID shopId) {
        var shop = shops.getForAdmin(shopId);

        return new AdminShopDetailResponse(
            shop,
            catalog.countProductsByShop(shopId),
            orders.countByShop(shopId),
            shops.historyOf(shopId)
        );
    }

    /**
     * Duyệt shop + cấp role SHOP cho owner để vào được Seller Center.
     */
    @Transactional
    public ShopResponse approve(@NonNull UUID adminId, @NonNull UUID shopId) {
        var approved = shops.approve(adminId, shopId);

        var ownerId = shops.ownerOf(shopId);
        if (ownerId != null && users.grantRole(ownerId, UserRole.SHOP)) {
            gotrue.setRole(ownerId, "shop");
        }

        return approved;
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID shopId, @NonNull String reason) {
        return shops.reject(adminId, shopId, reason);
    }

    @Transactional
    public ShopResponse suspend(@NonNull UUID adminId, @NonNull UUID shopId, @NonNull String reason) {
        return shops.suspend(adminId, shopId, reason);
    }

    @Transactional
    public ShopResponse reinstate(@NonNull UUID adminId, @NonNull UUID shopId) {
        return shops.reinstate(adminId, shopId);
    }

    @Transactional
    public ShopResponse setTrusted(@NonNull UUID adminId, @NonNull UUID shopId, boolean trusted) {
        return shops.setTrusted(adminId, shopId, trusted);
    }
}
