package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Buyer checkout COD va theo doi don")
public class OrderController {

    private final OrderService orders;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Checkout COD", description = "1 don dung 1 shop, tao don PENDING, chua tru kho.")
    @ApiResponse(responseCode = "201", description = "Don vua tao")
    @ApiResponse(responseCode = "400", description = "Item khac shop hoac body khong hop le")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Shop chua duoc duyet")
    @ApiResponse(responseCode = "404", description = "Variant khong ton tai")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse checkout(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CheckoutRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return orders.checkout(buyerId, request);
    }

    @GetMapping("/mine")
    @Operation(summary = "Don cua toi")
    @ApiResponse(responseCode = "200", description = "Danh sach don cua buyer")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<OrderResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return orders.getMine(buyerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiet don", description = "Chi buyer so huu moi xem duoc.")
    @ApiResponse(responseCode = "200", description = "Chi tiet don")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai hoac khong phai cua ban")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse detail(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return orders.getDetail(buyerId, id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Huy don", description = "Chi huy duoc khi PENDING.")
    @ApiResponse(responseCode = "200", description = "Don sau khi huy")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai hoac khong phai cua ban")
    @ApiResponse(responseCode = "409", description = "Don khong còn o trang thai PENDING")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse cancel(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return orders.cancel(buyerId, id);
    }

    @PatchMapping("/{id}/received")
    @Operation(summary = "Xac nhan da nhan hang", description = "Chi xac nhan duoc don DELIVERED. Mo khoa viet danh gia.")
    @ApiResponse(responseCode = "200", description = "Don sau khi xac nhan")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Don khong ton tai hoac khong phai cua ban")
    @ApiResponse(responseCode = "409", description = "Don chua o trang thai DELIVERED")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse confirmReceipt(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id don") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return orders.confirmReceipt(buyerId, id);
    }
}
