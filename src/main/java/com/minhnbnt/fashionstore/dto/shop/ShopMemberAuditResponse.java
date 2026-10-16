package com.minhnbnt.fashionstore.dto.shop;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ShopMemberAuditResponse(
    UUID id,
    UUID shopId,
    UUID memberId,
    String action,
    @Nullable String targetType,
    @Nullable UUID targetId,
    @Nullable String detail,
    Instant createdAt
) {
}
