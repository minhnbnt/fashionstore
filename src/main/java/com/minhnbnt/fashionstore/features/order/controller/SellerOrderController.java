package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
@Tag(name = "Seller Orders", description = "Seller xử lý đơn của shop mình")
public class SellerOrderController {

    private final OrderService orders;

    @GetMapping
    @Operation(summary = "Đơn của shop", description = "OWNER/STAFF, gom mọi shop mình tham gia. Hỗ trợ lọc theo shop, status, từ khóa.")
    @ApiResponse(responseCode = "200", description = "Danh sách đơn")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<OrderResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Lọc theo shop cụ thể") @RequestParam(required = false) UUID shopId,
        @Parameter(description = "Lọc theo trạng thái (PENDING, CONFIRMED, SHIPPING, DELIVERED, CANCELLED)") @RequestParam(required = false) String status,
        @Parameter(description = "Tìm theo mã đơn hoặc tên người nhận") @RequestParam(required = false) String q,
        @Parameter(description = "Trang bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số đơn mỗi trang") @RequestParam(defaultValue = "20") int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.listSellerOrdersFiltered(userId, shopId, status, q, page, limit);
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận đơn", description = "PENDING -> CONFIRMED, trừ kho nguyên tử.")
    @ApiResponse(responseCode = "200", description = "Đơn sau xác nhận")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @ApiResponse(responseCode = "409", description = "Hết kho hoặc sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse confirm(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id đơn") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.confirm(userId, id);
    }

    @PatchMapping("/{id}/ship")
    @Operation(summary = "Giao cho vận chuyển", description = "CONFIRMED -> SHIPPING.")
    @ApiResponse(responseCode = "200", description = "Đơn sau khi ship")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse ship(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id đơn") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.ship(userId, id);
    }

    @PatchMapping("/{id}/deliver")
    @Operation(summary = "Hoàn tất đơn COD", description = "SHIPPING -> DELIVERED, thu tiền COD.")
    @ApiResponse(responseCode = "200", description = "Đơn sau khi giao")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse deliver(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id đơn") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.deliver(userId, id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Seller hủy đơn", description = "PENDING hoặc CONFIRMED -> CANCELLED. Nếu đã CONFIRMED, tự động hoàn kho nguyên tử và ghi nhật ký tồn kho.")
    @ApiResponse(responseCode = "200", description = "Đơn sau khi hủy")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái đơn")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponse cancel(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id đơn") @PathVariable UUID id,
        @Valid @RequestBody CancelOrderRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return orders.sellerCancel(userId, id, request);
    }
}
