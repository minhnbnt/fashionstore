package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin lệnh rút tiền của Shop trên trang quản trị")
public record AdminWithdrawalResponse(
    @Schema(description = "ID lệnh rút tiền")
    UUID id,
    @Schema(description = "ID cửa hàng")
    UUID shopId,
    @Schema(description = "Tên cửa hàng")
    String shopName,
    @Schema(description = "Số tiền rút (VND)")
    long amount,
    @Schema(description = "Tên ngân hàng nhận tiền")
    String bankName,
    @Schema(description = "Số tài khoản ngân hàng")
    String bankAccount,
    @Schema(description = "Tên chủ tài khoản")
    String bankHolder,
    @Schema(description = "Trạng thái lệnh rút: REQUESTED, PROCESSING, COMPLETED, REJECTED")
    String status,
    @Schema(description = "Thời điểm tạo yêu cầu rút tiền")
    Instant createdAt,
    @Schema(description = "Thời điểm xử lý yêu cầu rút tiền")
    @Nullable Instant processedAt
) {
}
