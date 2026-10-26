package com.minhnbnt.fashionstore.dto.bank;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu tra cứu tên chủ tài khoản ngân hàng qua Napas")
public record LookupAccountRequest(
    @Schema(description = "Mã BIN 6 số của ngân hàng", example = "970436")
    @NotBlank(message = "Bank BIN is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Bank BIN must be 6 digits")
    String bin,

    @Schema(description = "Số tài khoản ngân hàng cần tra cứu", example = "0011001234567")
    @NotBlank(message = "Bank account number is required")
    @Size(min = 6, max = 25, message = "Bank account number must be between 6 and 25 characters")
    String accountNumber
) {}
