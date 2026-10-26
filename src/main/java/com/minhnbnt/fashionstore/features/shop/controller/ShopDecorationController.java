package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.DecorationResponse;
import com.minhnbnt.fashionstore.features.shop.ShopDecorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/decoration")
@RequiredArgsConstructor
@Tag(name = "Shops - Decoration", description = "Xem bố cục trang trí gian hàng public")
public class ShopDecorationController {

    private final ShopDecorationService decorations;

    @GetMapping
    @Operation(
        operationId = "getPublishedLayout",
        summary = "Xem giao diện gian hàng (Public)",
        description = "Lấy layout các block đã được shop xuất bản để hiển thị ở trang chủ shop."
    )
    @ApiResponse(responseCode = "200", description = "Layout gian hàng đã xuất bản")
    public DecorationResponse getPublishedLayout(
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        return decorations.getPublishedLayout(shopId);
    }
}
