package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderResponse(
    UUID id,

    @Schema(example = "ORD-AB12CD34")
    String code,

    UUID shopId,

    String shopName,

    String shopSlug,

    UUID buyerId,

    @Schema(description = "Email tài khoản người mua", example = "buyer@example.com")
    String buyerEmail,

    @Schema(description = "Tên người nhận", example = "Nguyễn An")
    String shippingName,

    @Schema(description = "SĐT người nhận", example = "0901234567")
    String shippingPhone,

    @Schema(description = "PENDING | CONFIRMED | SHIPPING | DELIVERED | CANCELLED", example = "PENDING")
    String status,

    @Schema(description = "UNPAID | COLLECTED | FAILED", example = "UNPAID")
    String paymentStatus,

    @Schema(description = "Tổng tiền đơn (VND)", example = "259000")
    long total,

    @Schema(description = "Tổng số lượng sản phẩm trong đơn")
    long itemCount,

    @Schema(description = "Tên sản phẩm đầu tiên, để nhận diện nhanh", nullable = true)
    @Nullable
    String firstItemName,

    @Schema(
        description = "Cảnh báo SLA: CONFIRM_LATE | HANDOVER_LATE | DELIVERY_LATE | RETURN_OPEN, null nếu bình thường",
        nullable = true,
        example = "CONFIRM_LATE"
    )
    @Nullable
    String alert,

    @Schema(
        description = "Trạng thái yêu cầu trả hàng gần nhất, null nếu không có",
        nullable = true,
        example = "REQUESTED"
    )
    @Nullable
    String returnStatus,

    @Schema(description = "Ai hủy đơn: BUYER | SELLER | ADMIN, null nếu chưa hủy", nullable = true, example = "ADMIN")
    @Nullable
    String cancelledBy,

    @Schema(description = "Mã lý do hủy, null nếu chưa hủy", nullable = true, example = "FRAUD_SUSPECTED")
    @Nullable
    String cancelReason,

    Instant createdAt,

    Instant updatedAt
) {
}
