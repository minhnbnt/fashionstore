package com.minhnbnt.fashionstore.test;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;

public final class ShopTestFixtures {
    private ShopTestFixtures() {}

    public static CreateShopRequest createRequest(String name, String slug) {
        return createRequest(name, slug, "Mô tả shop " + name);
    }

    public static CreateShopRequest createRequest(String name, String slug, String description) {
        return new CreateShopRequest(
            name,
            slug,
            description != null && !description.isBlank() ? description : "Mô tả shop " + name,
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
}
