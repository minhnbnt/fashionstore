package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateShopProfileRequest(
    @Schema(description = "Số điện thoại liên hệ", nullable = true)
    @Size(max = 20)
    @Nullable
    String phone,

    @Schema(description = "Địa chỉ kho / lấy hàng", nullable = true)
    @Size(max = 500)
    @Nullable
    String address,

    @Schema(description = "Chính sách đổi trả", nullable = true)
    @Size(max = 5000)
    @Nullable
    String returnPolicy,

    @Schema(description = "Số ngày chuẩn bị hàng (1-3 ngày)", nullable = true)
    @jakarta.validation.constraints.Min(value = 1, message = "Processing days must be at least 1")
    @jakarta.validation.constraints.Max(value = 3, message = "Processing days cannot exceed 3")
    @Nullable
    Integer processingDays,

    @Schema(description = "Tên ngân hàng", nullable = true)
    @Size(max = 100)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng", nullable = true)
    @Size(max = 50)
    @Nullable
    String bankAccount,

    @Schema(description = "Tên chủ tài khoản", nullable = true)
    @Size(max = 100)
    @Nullable
    String bankHolder,

    @Schema(description = "Tin nhắn tự động trả lời", nullable = true)
    @Size(max = 1000)
    @Nullable
    String autoReplyText,

    @Schema(description = "Giờ làm việc JSON", nullable = true)
    @Size(max = 2000)
    @Nullable
    String workingHours
) {
}
