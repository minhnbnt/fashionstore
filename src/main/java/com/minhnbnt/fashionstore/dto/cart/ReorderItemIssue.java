package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Chi tiết sản phẩm được điều chỉnh hoặc bỏ qua khi mua lại đơn hàng")
public record ReorderItemIssue(
    @Schema(description = "ID sản phẩm gốc", nullable = true)
    @Nullable
    UUID productId,

    @Schema(description = "ID biến thể gốc", nullable = true)
    @Nullable
    UUID variantId,

    @Schema(description = "Tên sản phẩm trong đơn cũ")
    String productName,

    @Schema(description = "Nhãn biến thể trong đơn cũ")
    String variantLabel,

    @Schema(
        description = "Mã lý do (OUT_OF_STOCK, STOCK_LIMITED, PRODUCT_UNAVAILABLE, "
            + "VARIANT_UNAVAILABLE, CART_MAXED)"
    )
    String reason,

    @Schema(description = "Số lượng trong đơn cũ")
    int requestedQuantity,

    @Schema(description = "Số lượng thực tế thêm được vào giỏ")
    int addedQuantity
) {
}

