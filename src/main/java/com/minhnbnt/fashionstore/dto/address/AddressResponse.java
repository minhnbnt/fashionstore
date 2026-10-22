package com.minhnbnt.fashionstore.dto.address;

import com.minhnbnt.fashionstore.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record AddressResponse(
    @Schema UUID id,
    @Schema(description = "Ten nguoi nhan") String recipientName,
    @Schema(description = "So dien thoai nguoi nhan") String phoneNumber,
    @Schema(description = "Dia chi chi tiet", nullable = true) @Nullable String detailAddress,
    @Schema(nullable = true) @Nullable String houseNumber,
    @Schema(nullable = true) @Nullable String street,
    @Schema(nullable = true) @Nullable String ward,
    @Schema(nullable = true) @Nullable String district,
    @Schema(nullable = true) @Nullable String city,
    @Schema(description = "Nhan dia chi") AddressLabel label,
    @Schema(description = "Dia chi mac dinh") Boolean defaultAddress
) {
}
