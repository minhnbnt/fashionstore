package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Thông tin giao dịch thanh toán VietQR và đối soát hoàn tiền trên trang quản trị")
public record PaymentTransactionResponse(
    @Schema(description = "ID giao dịch thanh toán")
    UUID id,

    @Schema(description = "ID người mua")
    UUID buyerId,

    @Schema(description = "Email người mua")
    String buyerEmail,

    @Schema(description = "Nhà cung cấp cổng thanh toán", example = "VIETQR")
    String provider,

    @Schema(description = "Mã tham chiếu giao dịch (nội dung chuyển khoản)", example = "DHMQ4F9A1C8E2B")
    String txnRef,

    @Schema(description = "Số tiền cần thanh toán (VND)")
    long amount,

    @Schema(description = "Số tiền thực nhận từ ngân hàng (VND)")
    long receivedAmount,

    @Schema(description = "Trạng thái giao dịch: PENDING, SUCCESS, UNDERPAID, FAILED, EXPIRED")
    String status,

    @Schema(description = "Mã viết tắt ngân hàng nhận tiền", example = "MB")
    String bankCode,

    @Schema(description = "Số tài khoản ngân hàng nhận tiền")
    String accountNumber,

    @Schema(description = "Mã giao dịch phía ngân hàng")
    @Nullable String bankTransactionId,

    @Schema(description = "Nội dung chuyển khoản thực tế từ ngân hàng")
    @Nullable String transferContent,

    @Schema(description = "Thời điểm thanh toán thành công")
    @Nullable Instant paidAt,

    @Schema(description = "Thời điểm hết hạn phiên thanh toán")
    Instant expiresAt,

    @Schema(description = "Trạng thái hoàn tiền: NONE, PENDING, REFUNDED")
    String refundStatus,

    @Schema(description = "Số tiền cần hoàn hoặc đã hoàn (VND)")
    long refundAmount,

    @Schema(description = "Lý do cần hoàn tiền")
    @Nullable String refundReason,

    @Schema(description = "Thời điểm Admin xác nhận đã hoàn tiền")
    @Nullable Instant refundedAt,

    @Schema(description = "Ghi chú đối soát hoàn tiền của Admin")
    @Nullable String refundNote,

    @Schema(description = "Danh sách đơn hàng liên kết với giao dịch")
    List<VietQrOrderBrief> orders,

    @Schema(description = "Thời điểm tạo giao dịch")
    Instant createdAt
) {
}
