package com.minhnbnt.fashionstore.features.payment.controller;

import com.minhnbnt.fashionstore.dto.payment.PaymentMethodsResponse;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(
    name = "Public - Payments",
    description = "Danh sách phương thức thanh toán khả dụng và webhook nhận biến động số dư VietQR."
)
public class PublicPaymentController {

    private final PaymentGatewayService gateways;

    @GetMapping("/payment-methods")
    @Operation(
        operationId = "listPaymentMethods",
        summary = "Danh sách phương thức thanh toán khả dụng",
        description = "Trả về các cổng thanh toán đang bật kèm hạn mức COD và số phút giữ đơn chờ thanh toán."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách phương thức thanh toán")
    public PaymentMethodsResponse listPaymentMethods() {
        return gateways.publicMethods();
    }
}
