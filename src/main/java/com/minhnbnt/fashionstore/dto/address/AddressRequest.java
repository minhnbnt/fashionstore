package com.minhnbnt.fashionstore.dto.address;

import com.minhnbnt.fashionstore.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record AddressRequest(
    @Schema(description = "Ten nguoi nhan", example = "Nguyen Van A", nullable = true)
    @Nullable @Size(max = 100) String recipientName,

    @Schema(description = "So dien thoai nguoi nhan", example = "0912345678", nullable = true)
    @Nullable @Size(max = 20) String phoneNumber,

    @Schema(description = "Dia chi chi tiet (so nha, toa nha, ngo ngach)", example = "So 18, ngo 12 Duy Tan", nullable = true)
    @Nullable @Size(max = 300) String detailAddress,

    @Schema(description = "So nha", nullable = true)
    @Nullable String houseNumber,

    @Schema(description = "Ten duong", nullable = true)
    @Nullable String street,

    @Schema(description = "Phuong/Xa", example = "Phuong Dich Vong Hau", nullable = true)
    @Nullable String ward,

    @Schema(description = "Quan/Huyen", example = "Quan Cau Giay", nullable = true)
    @Nullable String district,

    @Schema(description = "Tinh/Thanh pho", example = "Thanh pho Ha Noi", nullable = true)
    @Nullable String city,

    @Schema(description = "Nhan dia chi (HOME, OFFICE, OTHER)", example = "HOME", nullable = true)
    @Nullable AddressLabel label,

    @Schema(description = "Dat lam dia chi mac dinh", nullable = true)
    @Nullable Boolean defaultAddress
) {
}
