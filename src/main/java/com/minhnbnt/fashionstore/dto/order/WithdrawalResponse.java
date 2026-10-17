package com.minhnbnt.fashionstore.dto.order;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
    UUID id,
    UUID shopId,
    long amount,
    String bankName,
    String bankAccount,
    String bankHolder,
    String status,
    Instant createdAt,
    @Nullable Instant processedAt
) {
}
