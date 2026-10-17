package com.minhnbnt.fashionstore.dto.shop;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShopRoleResponse(
    UUID id,
    UUID shopId,
    String name,
    List<String> permissions,
    boolean isSystem,
    Instant createdAt
) {
}
