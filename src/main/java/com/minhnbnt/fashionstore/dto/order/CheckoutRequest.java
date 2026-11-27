package com.minhnbnt.fashionstore.dto.order;

import com.minhnbnt.fashionstore.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

    @Schema(description = "Phương thức thanh toán (mặc định COD)", example = "COD", nullable = true)
    @Nullable
    PaymentMethod paymentMethod,

    @Schema(description = "Mã giảm giá của shop áp dụng cho đơn này", nullable = true, example = "SUMMER50K")
    @Nullable
    String voucherCode
) {

    public CheckoutRequest(
        UUID shopId,
        List<CheckoutItemRequest> items,
        String shippingName,
        String shippingPhone,
        String shippingAddress
    ) {
        this(shopId, items, shippingName, shippingPhone, shippingAddress, PaymentMethod.COD, null);
    }

    public CheckoutRequest(
        UUID shopId,
        List<CheckoutItemRequest> items,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        @Nullable PaymentMethod paymentMethod
    ) {
        this(shopId, items, shippingName, shippingPhone, shippingAddress, paymentMethod, null);
    }

    public PaymentMethod resolvedPaymentMethod() {
        if (paymentMethod == null) {
            return PaymentMethod.COD;
        }
        return paymentMethod;
    }
}
