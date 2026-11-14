package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record OrderItemResponse(
    @Schema(description = "ID dòng sản phẩm trong đơn")
    UUID id,

    @Schema(nullable = true)
    @Nullable
    UUID productId,

    @Schema(nullable = true)
    @Nullable
    UUID variantId,

    @Schema(description = "Tên sản phẩm")
    String productName,

    @Schema(description = "Phân loại biến thể")
    String variantLabel,

    @Schema(description = "Mã SKU")
    String sku,

    @Schema(description = "Đơn giá lúc mua")
    long unitPrice,

    @Schema(description = "Số lượng")
    int quantity,

    @Schema(description = "Thành tiền")
    long lineTotal,

    @Schema(description = "URL public ảnh đại diện sản phẩm/biến thể", nullable = true)
    @Nullable
    String imageUrl,

    @Schema(description = "Sản phẩm còn đang mở bán hay đã ẩn/ngừng bán")
    boolean productAvailable
) {
}
