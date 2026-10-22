package com.minhnbnt.fashionstore.dto.address;

import com.minhnbnt.fashionstore.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @Schema(description = "Ten nguoi nhan", example = "Nguyen Van A")
    @NotBlank
    @Size(max = 100)
    String recipientName,

    @Schema(description = "So dien thoai nguoi nhan", example = "0912345678")
    @NotBlank
    @Size(max = 20)
    String phoneNumber,

    @Schema(
        description = "Dia chi day du do frontend build tu API locations (so nha + xa/phuong + tinh/thanh)",
        example = "So 18 Duy Tan, Phuong Dich Vong, Ha Noi"
    )
    @NotBlank
    @Size(max = 300)
    String fullAddress,

    @Schema(description = "Nhan dia chi (HOME, OFFICE, OTHER)", example = "HOME")
    @NotNull
    AddressLabel label,

    @Schema(description = "Dat lam dia chi mac dinh")
    boolean defaultAddress
) {
}
