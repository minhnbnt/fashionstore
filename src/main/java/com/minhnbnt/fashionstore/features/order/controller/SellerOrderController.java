package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
@Tag(name = "Seller Orders", description = "Seller xu ly don cua shop minh")
public class SellerOrderController {

    private final OrderService orders;

    @GetMapping
    @Operation(summary = "Don cua shop", description = "OWNER/STAFF, gom moi shop minh tham gia.")
    @ApiResponse(responseCode = "200", description = "Danh sach don")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.listSellerOrders(userId);
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Xac nhan don", description = "PENDING -> CONFIRMED, tru kho nguyen tu.")
    @ApiResponse(responseCode = "200", description = "Don sau xac nhan")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Khong phai thanh vien shop")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai")
    @ApiResponse(responseCode = "409", description = "Het kho hoac sai trang thai")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse confirm(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.confirm(userId, id);
    }

    @PatchMapping("/{id}/ship")
    @Operation(summary = "Giao cho van chuyen", description = "CONFIRMED -> SHIPPING.")
    @ApiResponse(responseCode = "200", description = "Don sau khi ship")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Khong phai thanh vien shop")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai")
    @ApiResponse(responseCode = "409", description = "Sai trang thai")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse ship(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.ship(userId, id);
    }

    @PatchMapping("/{id}/deliver")
    @Operation(summary = "Hoan tat don COD", description = "SHIPPING -> DELIVERED, thu tien COD.")
    @ApiResponse(responseCode = "200", description = "Don sau khi giao")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Khong phai thanh vien shop")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai")
    @ApiResponse(responseCode = "409", description = "Sai trang thai")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse deliver(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.deliver(userId, id);
    }
}
