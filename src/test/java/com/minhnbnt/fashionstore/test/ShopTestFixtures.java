package com.minhnbnt.fashionstore.test;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.model.Shop;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public final class ShopTestFixtures {
    private ShopTestFixtures() {}

    public static CreateShopRequest createRequest(String name, String slug) {
        return createRequest(name, slug, "Mô tả shop " + name);
    }

    public static CreateShopRequest createRequest(String name, String slug, String description) {
        var desc = description;
        if (desc == null || desc.isBlank()) {
            desc = "Mô tả shop " + name;
        }
        return new CreateShopRequest(
            name,
            slug,
            desc,
            "test/logo.webp",
            "test/banner.webp",
            "0901234567",
            "123 Đường Thời Trang, Hà Nội",
            "INDIVIDUAL",
            "NGUYEN VAN A",
            "001202001234",
            "01/01/2021",
            "Cục Cảnh sát QLHC về TTXH",
            null,
            "test/id-front.webp",
            "test/id-back.webp",
            "test/id-selfie.webp",
            null,
            "Vietcombank",
            "0123456789",
            "NGUYEN VAN A"
        );
    }

    public static Shop shop(String name, String slug, String description) {
        return Shop.create(
            name, slug, description,
            null, null, null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null
        );
    }

    public static ShopResponse response(UUID id, String status) {
        return response(id, status, null);
    }

    public static ShopResponse response(UUID id, String status, @Nullable String statusReason) {
        return new ShopResponse(
            id, "Shop", "shop", "Mo ta", null, null, status, statusReason,
            null, null, null, 2, "INDIVIDUAL", null, 0, 0,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null,
            "PENDING_REVIEW", null,
            Instant.now()
        );
    }
}
