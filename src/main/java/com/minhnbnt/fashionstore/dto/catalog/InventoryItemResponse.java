package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record InventoryItemResponse(
    UUID variantId,
    UUID productId,
    String productName,
    String sku,
    String size,
    String color,
    long price,
    int stockQuantity,
    int lowStockThreshold,
    boolean isLowStock
) {
}
