package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    List<ReturnRequestResponse> returns,

    @Schema(description = "ID voucher của shop đã áp dụng", nullable = true)
    @Nullable
    UUID voucherId,

    @Schema(description = "Mã voucher của shop đã áp dụng", nullable = true, example = "SUMMER50K")
    @Nullable
    String voucherCode,

    @Schema(description = "Số tiền được giảm từ voucher của shop, VND", example = "50000")
    long discountAmount
) {

    public AdminOrderDetailResponse(
        AdminOrderResponse order,
        String shopStatus,
        String paymentMethod,
        long subtotal,
        long shippingFee,
        String shippingAddress,
        String note,
        @Nullable String cancelNote,
        @Nullable Instant confirmedAt,
        @Nullable Instant shippedAt,
        @Nullable Instant deliveredAt,
        @Nullable Instant cancelledAt,
        List<OrderItemResponse> items,
        List<OrderTimelineResponse> timeline,
        List<ReturnRequestResponse> returns
    ) {
        this(
            order,
            shopStatus,
            paymentMethod,
            subtotal,
            shippingFee,
            shippingAddress,
            note,
            cancelNote,
            confirmedAt,
            shippedAt,
            deliveredAt,
            cancelledAt,
            items,
            timeline,
            returns,
            null,
            null,
            0L
        );
    }
}
