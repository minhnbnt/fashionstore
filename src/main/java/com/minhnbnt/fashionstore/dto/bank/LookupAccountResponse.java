package com.minhnbnt.fashionstore.dto.bank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả tra cứu tên chủ tài khoản ngân hàng")
public record LookupAccountResponse(
    @Schema(description = "Mã ngân hàng", example = "VCB")
    String bankCode,

    @Schema(description = "Số tài khoản", example = "0011001234567")
    String accountNumber,

    @Schema(description = "Tên chủ tài khoản đã đăng ký tại ngân hàng", example = "NGUYEN VAN A")
    String accountName
) {}
