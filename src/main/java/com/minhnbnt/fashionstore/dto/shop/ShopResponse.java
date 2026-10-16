package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ShopResponse(
    UUID id,

    String name,

    String slug,

    String description,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String bannerKey,

    @Schema(example = "APPROVED")
    String status,

    @Schema(description = "Số điện thoại liên hệ", nullable = true)
    @Nullable
    String phone,

    @Schema(description = "Địa chỉ lấy hàng", nullable = true)
    @Nullable
    String address,

    @Schema(description = "Chính sách đổi trả", nullable = true)
    @Nullable
    String returnPolicy,

    int processingDays,

    String sellerType,

    @Schema(description = "Đánh giá trung bình", nullable = true)
    @Nullable
    Double rating,

    int ratingCount,

    int followerCount,

    @Schema(description = "Tên ngân hàng", nullable = true)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản", nullable = true)
    @Nullable
    String bankAccount,

    @Schema(description = "Chủ tài khoản", nullable = true)
    @Nullable
    String bankHolder,

    @Schema(description = "Tin nhắn tự động", nullable = true)
    @Nullable
    String autoReplyText,

    @Schema(description = "Giờ làm việc", nullable = true)
    @Nullable
    String workingHours,

    Instant createdAt
) {
    public ShopResponse(
        UUID id,
        String name,
        String slug,
        String description,
        @Nullable String logoKey,
        @Nullable String bannerKey,
        String status,
        Instant createdAt
    ) {
        this(
            id, name, slug, description, logoKey, bannerKey, status,
            null, null, null, 2, "INDIVIDUAL", null, 0, 0,
            null, null, null, null, null, createdAt
        );
    }
}
