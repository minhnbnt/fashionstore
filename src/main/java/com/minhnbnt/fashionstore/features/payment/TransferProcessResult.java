package com.minhnbnt.fashionstore.features.payment;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record TransferProcessResult(
    TransferOutcome outcome,
    @Nullable String txnRef,
    long expectedAmount,
    long receivedAmount,
    List<UUID> affectedOrderIds
) {
}
