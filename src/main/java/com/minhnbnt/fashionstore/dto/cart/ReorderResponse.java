package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Kết quả thao tác mua lại đơn hàng vào giỏ")
public record ReorderResponse(
    @Schema(description = "Trạng thái giỏ hàng sau khi thêm")
    CartResponse cart,

    @Schema(description = "Tổng số dòng sản phẩm từ đơn cũ")
    int totalRequestedItems,

    @Schema(
        description = "Số dòng đã thêm được vào giỏ (gồm cả dòng bị điều chỉnh số lượng)"
    )
    int addedCount,

    @Schema(
        description = "Số dòng chỉ thêm được một phần do tồn kho còn lại "
            + "thấp hơn số lượng cũ"
    )
    int adjustedCount,

    @Schema(description = "Số dòng không thể thêm lại")
    int skippedCount,

    @Schema(description = "Danh sách các dòng bị giảm số lượng khi thêm vào giỏ")
    List<ReorderItemIssue> adjustedItems,

    @Schema(description = "Danh sách các dòng bị bỏ qua hoàn toàn")
    List<ReorderItemIssue> skippedItems
) {
}

