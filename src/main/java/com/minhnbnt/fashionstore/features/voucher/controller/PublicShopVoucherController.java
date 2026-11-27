package com.minhnbnt.fashionstore.features.voucher.controller;

import com.minhnbnt.fashionstore.dto.voucher.PublicShopVoucherResponse;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/vouchers")
@RequiredArgsConstructor
@Tag(name = "Shop Vouchers", description = "Danh sách mã giảm giá công khai của gian hàng")
public class PublicShopVoucherController {

    private final VoucherService vouchers;

    @GetMapping
    @Operation(
        operationId = "listPublicShopVouchers",
        summary = "Danh sách mã giảm giá công khai của gian hàng",
        description = "Trả về các mã giảm giá PUBLIC đang diễn ra hoặc sắp diễn ra của gian hàng."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách mã giảm giá công khai")
    public List<PublicShopVoucherResponse> listPublicShopVouchers(
        @AuthenticationPrincipal @Nullable Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId
    ) {
        UUID buyerId = null;
        if (jwt != null && jwt.getSubject() != null) {
            buyerId = UUID.fromString(jwt.getSubject());
        }

        return vouchers.listPublicForShop(shopId, buyerId);
    }
}
