package com.minhnbnt.fashionstore.dto.address;

import com.minhnbnt.fashionstore.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AddressResponse(
    @Schema UUID id,
    @Schema(description = "Ten nguoi nhan") String recipientName,
    @Schema(description = "So dien thoai nguoi nhan") String phoneNumber,
    @Schema(description = "Dia chi day du") String fullAddress,
    @Schema(description = "Nhan dia chi") AddressLabel label,
    @Schema(description = "Dia chi mac dinh") boolean defaultAddress
) {
}
