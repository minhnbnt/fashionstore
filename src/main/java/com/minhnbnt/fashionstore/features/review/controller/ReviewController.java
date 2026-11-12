package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewSummaryResponse;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.model.ReviewSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Operation(
        summary = "Liet ke danh gia",
        description = "Public (nhan JWT neu da dang nhap de danh dau helpfulByMe/mine). Mac dinh sap xep huu ich nhat truoc."
    )
    @ApiResponse(responseCode = "200", description = "Danh sach danh gia")
    @ApiResponse(responseCode = "400", description = "Bo loc khong hop le")
    @ApiResponse(responseCode = "404", description = "Product khong ton tai hoac dang an")
    public List<ReviewResponse> list(
        @AuthenticationPrincipal @Nullable Jwt jwt,
        @Parameter(description = "id product") @PathVariable UUID productId,
        @Parameter(description = "Thu tu sap xep (HELPFUL | NEWEST)")
        @RequestParam(defaultValue = "HELPFUL") ReviewSort sort,
        @Parameter(description = "Loc theo so sao (1-5)")
        @RequestParam(required = false) @Nullable Integer stars,
        @Parameter(description = "Chi xem danh gia co anh hoac video")
        @RequestParam(defaultValue = "false") boolean hasMedia
    ) {
        var currentUserId = extractUserId(jwt);
        return reviews.getReviews(productId, currentUserId, sort, stars, hasMedia);
    }

    @GetMapping("/summary")
    @Operation(
        summary = "Tong hop danh gia san pham",
        description = "Public. Tra diem trung binh, tong so danh gia va phan bo 5 -> 1 sao."
    )
    @ApiResponse(responseCode = "200", description = "Thong ke danh gia")
    @ApiResponse(responseCode = "404", description = "Product khong ton tai hoac dang an")
    public ReviewSummaryResponse summary(
        @Parameter(description = "id product") @PathVariable UUID productId
    ) {
        return reviews.reviewSummary(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Viet danh gia",
        description = "Chi buyer da hoan thanh don hang (DELIVERED + da xac nhan nhan hang) moi viet duoc, moi (don hang, product) 1 bai."
    )
    @ApiResponse(responseCode = "201", description = "Danh gia vua tao")
    @ApiResponse(responseCode = "400", description = "Du lieu danh gia khong hop le hoac san pham khong thuoc don hang")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "403", description = "Don hang chua hoan thanh")
    @ApiResponse(responseCode = "404", description = "Product hoac don hang khong ton tai")
    @ApiResponse(responseCode = "409", description = "Da danh gia san pham nay cho don hang nay roi")
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

    private static @Nullable UUID extractUserId(@Nullable Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        var subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }
        return UUID.fromString(subject);
    }
}
