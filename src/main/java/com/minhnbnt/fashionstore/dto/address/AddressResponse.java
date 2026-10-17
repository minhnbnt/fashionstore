package com.minhnbnt.fashionstore.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record AddressResponse(
    @Schema UUID id,
    @Schema(nullable = true) @Nullable String houseNumber,
    @Schema(nullable = true) @Nullable String street,
    @Schema(nullable = true) @Nullable String ward,
    @Schema(nullable = true) @Nullable String district,
    @Schema(nullable = true) @Nullable String city,
    @Schema Boolean defaultAddress
) {
}
