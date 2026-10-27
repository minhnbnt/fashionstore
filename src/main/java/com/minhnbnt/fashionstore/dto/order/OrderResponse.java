package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    @Schema(description = "ID đơn hàng")
    UUID id,

    @Schema(description = "Mã đơn hàng", example = "ORD-AB12CD34")
    String code,

    @Schema(description = "ID shop bán")
    UUID shopId,

    @Schema(description = "ID người mua")
    UUID buyerId,

    @Schema(description = "Trạng thái đơn hàng", example = "PENDING")
    String status,

    @Schema(description = "Trạng thái thanh toán", example = "UNPAID")
    String paymentStatus,

    @Schema(description = "Phương thức thanh toán", example = "COD")
    String paymentMethod,

    @Schema(description = "Tiền hàng", example = "250000")
    long subtotal,

    @Schema(description = "Phí vận chuyển", example = "30000")
    long shippingFee,

    @Schema(description = "Tổng thanh toán", example = "280000")
    long total,

    @Schema(description = "Tên người nhận", example = "Nguyễn Văn A")
    String shippingName,

    @Schema(description = "SĐT người nhận", example = "0901234567")
    String shippingPhone,

    @Schema(description = "Địa chỉ nhận hàng", example = "123 Phố Huế, Hà Nội")
    String shippingAddress,

    @Nullable
    @Schema(description = "Ghi chú của người mua", example = "Giao giờ hành chính")
    String note,

    @Nullable
    @Schema(description = "Lý do hủy đơn", example = "Hết hàng trong kho")
    String cancelReason,

    @Nullable
    @Schema(description = "Ghi chú khi hủy", example = "Kho đã hết size M")
    String cancelNote,

    @Nullable
    @Schema(description = "Bên thực hiện hủy đơn (BUYER, SELLER, ADMIN, SYSTEM)", example = "SYSTEM")
    String cancelledBy,

    @Nullable
    @Schema(description = "Thời điểm shop xác nhận đơn")
    Instant confirmedAt,

    @Nullable
    @Schema(description = "Thời điểm đơn bị hủy")
    Instant cancelledAt,

    @Schema(description = "Danh sách sản phẩm trong đơn")
    List<OrderItemResponse> items,

    @Schema(description = "Thời điểm tạo đơn")
    Instant createdAt
) {
}
