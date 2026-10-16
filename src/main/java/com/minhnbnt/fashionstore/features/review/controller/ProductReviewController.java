package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.CreateProductReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ProductReviewResponse;
import com.minhnbnt.fashionstore.features.review.ProductReviewService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Đánh giá sản phẩm")
public class ProductReviewController {

    private final ProductReviewService reviews;

    @PostMapping("/api/orders/{orderId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đánh giá sản phẩm sau mua", description = "Chỉ áp dụng cho đơn DELIVERED, tối đa 1 đánh giá cho mỗi sản phẩm trong đơn.")
    @ApiResponse(responseCode = "201", description = "Đánh giá thành công")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Đơn hàng không tồn tại")
    @ApiResponse(responseCode = "409", description = "Đơn chưa giao hoặc đã được đánh giá")
    @SecurityRequirement(name = "bearerAuth")
    public ProductReviewResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id đơn hàng") @PathVariable UUID orderId,
        @Valid @RequestBody CreateProductReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return reviews.create(buyerId, orderId, request);
    }

    @GetMapping("/api/products/{productId}/reviews")
    @Operation(summary = "Xem đánh giá sản phẩm (Public)", description = "Danh sách đánh giá công khai của sản phẩm.")
    @ApiResponse(responseCode = "200", description = "Danh sách đánh giá")
    public List<ProductReviewResponse> listByProduct(
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId
    ) {
        return reviews.listByProduct(productId);
    }
}
