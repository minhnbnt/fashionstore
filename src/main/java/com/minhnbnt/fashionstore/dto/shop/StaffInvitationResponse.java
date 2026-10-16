package com.minhnbnt.fashionstore.dto.shop;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationResponse(
    UUID id,
    UUID shopId,
    String email,
    UUID roleId,
    String token,
    Instant expiresAt,
    @Nullable Instant acceptedAt,
    Instant createdAt
) {
}
