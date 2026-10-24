package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CartCheckoutRequest(
    @Schema(example = "An")
    @NotBlank
    @Size(max = 200)
    String shippingName,

    @Schema(example = "0900000000")
    @NotBlank
    @Size(max = 30)
    String shippingPhone,

    @Schema(example = "Ha Noi")
    @NotBlank
    @Size(max = 1000)
    String shippingAddress,

    @Schema(description = "Phi ship VND ap cho moi don, null = 0", nullable = true)
    @Nullable
    @Min(0)
    Long shippingFee,

    @Schema(
        description = "Chi thanh toan cac variant nay, null/rong = toan bo gio",
        nullable = true
    )
    @Nullable
    List<UUID> variantIds
) {
}
