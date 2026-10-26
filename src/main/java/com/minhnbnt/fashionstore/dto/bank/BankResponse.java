package com.minhnbnt.fashionstore.dto.bank;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Thông tin ngân hàng trong mạng lưới Napas")
public record BankResponse(
    @Schema(description = "ID ngân hàng", example = "17")
    String id,

    @Schema(
        description = "Tên đầy đủ của ngân hàng",
        example = "Ngân hàng TMCP Ngoại Thương Việt Nam"
    )
    String name,

    @Schema(description = "Mã viết tắt / code", example = "VCB")
    String code,

    @Schema(description = "Mã định danh ngân hàng BIN (6 số)", example = "970436")
    String bin,

    @Schema(description = "Tên giao dịch thường gọi", example = "Vietcombank")
    String shortName,

    @Schema(description = "URL logo ngân hàng", example = "https://api.vietqr.io/img/VCB.png")
    @Nullable String logo,

    @Schema(description = "Ngân hàng có hỗ trợ tra cứu tên tài khoản không", example = "true")
    boolean lookupSupported
) {}
