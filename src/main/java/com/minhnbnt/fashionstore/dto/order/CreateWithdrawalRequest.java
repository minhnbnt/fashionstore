package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record CreateWithdrawalRequest(
    @NotNull
    UUID shopId,

    @Schema(description = "Số tiền rút (VND, tối thiểu 50.000)", example = "1000000")
    @NotNull
    @Min(50000)
    Long amount,

    @Schema(description = "Tên ngân hàng (để trống sẽ lấy theo hồ sơ shop)", nullable = true)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng (để trống sẽ lấy theo hồ sơ shop)", nullable = true)
    @Nullable
    String bankAccount,

    @Schema(description = "Chủ tài khoản (để trống sẽ lấy theo hồ sơ shop)", nullable = true)
    @Nullable
    String bankHolder
) {
}
