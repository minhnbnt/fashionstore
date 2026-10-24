package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(
    AdminOrderResponse order,

    @Schema(description = "Trạng thái shop hiện tại", example = "APPROVED")
    String shopStatus,

    @Schema(example = "COD")
    String paymentMethod,

    long subtotal,

    long shippingFee,

    String shippingAddress,

    @Schema(description = "Ghi chú của người mua")
    String note,

    @Schema(description = "Ghi chú khi hủy", nullable = true)
    @Nullable
    String cancelNote,

    @Schema(nullable = true)
    @Nullable
    Instant confirmedAt,

    @Schema(nullable = true)
    @Nullable
    Instant shippedAt,

    @Schema(nullable = true)
    @Nullable
    Instant deliveredAt,

    @Schema(nullable = true)
    @Nullable
    Instant cancelledAt,

    List<OrderItemResponse> items,

    @Schema(description = "Dòng thời gian trạng thái, cũ nhất trước")
    List<OrderTimelineResponse> timeline,

    @Schema(description = "Yêu cầu trả hàng của đơn, mới nhất trước")
    List<ReturnRequestResponse> returns
) {
}
