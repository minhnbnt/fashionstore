package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.review.ReviewService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Danh gia cua buyer da nhan hang")
public class ReviewController {

    private final ReviewService reviews;

    @GetMapping
    @Operation(summary = "Liet ke danh gia", description = "Public, moi nhat truoc.")
    @ApiResponse(responseCode = "200", description = "Danh sach danh gia")
    @ApiResponse(responseCode = "404", description = "Product khong ton tai hoac dang an")
    public List<ReviewResponse> list(
        @Parameter(description = "id product") @PathVariable UUID productId
    ) {
        return reviews.getReviews(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Viet danh gia",
        description = "Chi buyer da nhan hang (don DELIVERED + da xac nhan) moi viet duoc, moi product 1 bai."
    )
    @ApiResponse(responseCode = "201", description = "Danh gia vua tao")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Chua nhan hang nay")
    @ApiResponse(responseCode = "404", description = "Product khong ton tai")
    @ApiResponse(responseCode = "409", description = "Da danh gia product nay roi")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id product") @PathVariable UUID productId,
        @Valid @RequestBody CreateReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return reviews.createReview(buyerId, productId, request);
    }

    @PutMapping
    @Operation(summary = "Sua danh gia cua minh")
    @ApiResponse(responseCode = "200", description = "Danh gia sau khi sua")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Ban chua danh gia product nay")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id product") @PathVariable UUID productId,
        @Valid @RequestBody UpdateReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return reviews.updateReview(buyerId, productId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoa danh gia cua minh")
    @ApiResponse(responseCode = "204", description = "Da xoa")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Ban chua danh gia product nay")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id product") @PathVariable UUID productId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        reviews.deleteReview(buyerId, productId);
    }
}
