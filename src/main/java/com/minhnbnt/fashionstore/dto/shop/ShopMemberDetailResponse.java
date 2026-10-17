package com.minhnbnt.fashionstore.dto.shop;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ShopMemberDetailResponse(
    UUID shopId,
    UUID userId,
    String role,
    @Nullable UUID roleId,
    @Nullable String roleName,
    @Nullable UUID invitedBy,
    @Nullable Instant invitedAt,
    String status,
    Instant joinedAt
) {
}
