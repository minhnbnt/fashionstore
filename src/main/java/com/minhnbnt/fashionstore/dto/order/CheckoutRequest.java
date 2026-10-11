package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
    @Schema(description = "Shop checkout, 1 don dung 1 shop")
    @NotNull
    UUID shopId,

    @Schema(description = "It nhat 1 item")
    @NotEmpty
    @Valid
    List<CheckoutItemRequest> items,

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

    @Schema(description = "Phi ship VND, null = 0", nullable = true)
    @Nullable
    @Min(0)
    Long shippingFee
) {
}
