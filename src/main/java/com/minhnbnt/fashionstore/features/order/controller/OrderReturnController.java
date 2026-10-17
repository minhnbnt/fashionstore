package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
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
@Tag(name = "Buyer - Returns", description = "Buyer yêu cầu đổi / trả hàng")
public class OrderReturnController {

    private final ReturnRequestService returns;

    @PostMapping("/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo yêu cầu đổi/trả hàng", description = "Chỉ áp dụng cho đơn DELIVERED.")
    @ApiResponse(responseCode = "201", description = "Yêu cầu đổi trả đã tạo")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại hoặc không phải của buyer")
    @ApiResponse(responseCode = "409", description = "Đơn chưa giao hoặc đã có yêu cầu xử lý")
    @SecurityRequirement(name = "bearerAuth")
    public ReturnRequestResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id đơn hàng") @PathVariable UUID id,
        @Valid @RequestBody CreateReturnRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return returns.create(buyerId, id, request);
    }

    @GetMapping("/returns/mine")
    @Operation(summary = "Danh sách yêu cầu đổi/trả của tôi")
    @ApiResponse(responseCode = "200", description = "Danh sách yêu cầu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<ReturnRequestResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return returns.listBuyerReturns(buyerId);
    }
}
