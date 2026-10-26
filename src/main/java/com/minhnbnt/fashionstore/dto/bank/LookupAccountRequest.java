package com.minhnbnt.fashionstore.dto.bank;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu tra cứu tên chủ tài khoản ngân hàng qua Napas")
public record LookupAccountRequest(
    @Schema(description = "Mã BIN 6 số của ngân hàng", example = "970436")
    @NotBlank(message = "Mã BIN ngân hàng không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã BIN phải gồm 6 chữ số")
    String bin,

    @Schema(description = "Số tài khoản ngân hàng cần tra cứu", example = "0011001234567")
    @NotBlank(message = "Số tài khoản không được để trống")
    @Size(min = 6, max = 25, message = "Số tài khoản phải từ 6 đến 25 ký tự")
    String accountNumber
) {}
