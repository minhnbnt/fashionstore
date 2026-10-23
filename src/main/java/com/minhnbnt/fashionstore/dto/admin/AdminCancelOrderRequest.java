package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.model.AdminCancelReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCancelOrderRequest(
    @Schema(description = "Loại lý do hủy", example = "FRAUD_SUSPECTED")
    @NotNull
    AdminCancelReason reason,

    @Schema(
        description = "Ghi chú hiển thị cho người mua, shop và lưu vào dòng thời gian",
        example = "Tài khoản đặt 20 đơn cùng SĐT trong 1 giờ, nghi đơn ảo"
    )
    @NotBlank
    @Size(min = 5, max = 500)
    String note
) {
}
