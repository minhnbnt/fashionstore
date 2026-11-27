package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VietQrSessionResponse(
    @Schema(description = "ID bản ghi giao dịch thanh toán")
    UUID transactionId,

    @Schema(description = "Mã tham chiếu nội dung chuyển khoản duy nhất", example = "DHMQ1234567890")
    String txnRef,

    @Schema(description = "Trạng thái giao dịch", example = "PENDING")
    String status,

    @Schema(description = "Tổng số tiền cần chuyển khoản (VND)", example = "410000")
    long amount,

    @Schema(description = "Số tiền ngân hàng thực nhận (VND)", example = "0")
    long receivedAmount,

    @Schema(description = "Mã BIN ngân hàng NAPAS", example = "970422")
    String bankBin,

    @Schema(description = "Mã viết tắt ngân hàng", example = "MB")
    String bankCode,

    @Schema(description = "Tên đầy đủ của ngân hàng", example = "Ngân hàng TMCP Quân đội (MB Bank)")
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng nhận tiền của sàn", example = "0900123456789")
    String accountNumber,

    @Schema(description = "Tên chủ tài khoản ngân hàng của sàn", example = "SAN TMDT FASHIONSTORE")
    String accountName,

    @Schema(description = "URL ảnh mã QR chuẩn VietQR")
    String qrImageUrl,

    @Schema(description = "Thời điểm hết hạn phiên thanh toán")
    Instant expiresAt,

    @Schema(description = "Thời điểm xác nhận thanh toán thành công")
    @Nullable Instant paidAt,

    @Schema(description = "Chế độ Demo Sandbox có đang bật hay không", example = "true")
    boolean sandboxMode,

    @Schema(description = "Trạng thái hoàn tiền", example = "NONE")
    String refundStatus,

    @Schema(description = "Số tiền cần hoàn hoặc đã hoàn (VND)", example = "0")
    long refundAmount,

    @Schema(description = "Lý do hoàn tiền")
    @Nullable String refundReason,

    @Schema(description = "Danh sách các đơn hàng thuộc phiên thanh toán")
    List<VietQrOrderBrief> orders
) {
}
