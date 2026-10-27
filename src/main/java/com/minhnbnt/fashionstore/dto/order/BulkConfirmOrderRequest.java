package com.minhnbnt.fashionstore.dto.order;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkConfirmOrderRequest(
    @NotEmpty
    List<UUID> orderIds
) {
}
