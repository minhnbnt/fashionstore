package com.minhnbnt.fashionstore.test;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.Upload;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public final class ShopTestFixtures {
    private ShopTestFixtures() {}

    /** Row upload thật trong DB — register() validate upload tồn tại + content-type. */
    public static CreateShopRequest createRequest(UploadRepository uploads, String name, String slug) {
        return createRequest(uploads, name, slug, "Mô tả shop " + name);
    }

    public static CreateShopRequest createRequest(
        UploadRepository uploads, String name, String slug, String description) {
        var desc = description;
        if (desc == null || desc.isBlank()) {
            desc = "Mô tả shop " + name;
        }
        return new CreateShopRequest(
            name,
            slug,
            desc,
            uploadId(uploads),
            uploadId(uploads),
            "0901234567",
            "123 Đường Thời Trang, Hà Nội",
            "INDIVIDUAL",
            "NGUYEN VAN A",
            "001202001234",
            "01/01/2021",
            "Cục Cảnh sát QLHC về TTXH",
            null,
            uploadId(uploads),
            uploadId(uploads),
            uploadId(uploads),
            null,
            "Vietcombank",
            "0123456789",
            "NGUYEN VAN A"
        );
    }

    private static UUID uploadId(UploadRepository uploads) {
        return uploads.save(Upload.create(UUID.randomUUID() + ".webp", "image/webp")).getId();
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
            id, "Shop", "shop", "Mo ta", null, null, null, null, status, statusReason,
            null, null, null, 2, "INDIVIDUAL", null, 0, 0,
            null, null, null, null, null,
            "PENDING_REVIEW", null,
            false,
            Instant.now()
        );
    }
}
