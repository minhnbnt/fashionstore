package com.minhnbnt.fashionstore.dto.order;

import java.util.UUID;

public record BulkConfirmFailureItem(
    UUID orderId,
    String code,
    String reason
) {
}
