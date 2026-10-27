package com.minhnbnt.fashionstore.dto.order;

import java.util.List;
import java.util.UUID;

public record BulkConfirmOrderResponse(
    int totalRequested,
    int successCount,
    int failureCount,
    List<UUID> successfulOrderIds,
    List<BulkConfirmFailureItem> failures
) {
}
