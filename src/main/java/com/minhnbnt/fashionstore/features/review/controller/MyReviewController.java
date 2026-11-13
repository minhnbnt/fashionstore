package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.ReviewHelpfulResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Danh gia cua buyer da nhan hang")
public class MyReviewController {

    private final ReviewService reviews;

    @GetMapping("/mine")
    @Operation(
        summary = "Danh sach danh gia cua toi",
        description = "Tra danh gia cua buyer hien tai, moi cap nhat truoc."
    )
    @ApiResponse(responseCode = "200", description = "Danh sach danh gia cua buyer")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<ReviewResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return reviews.listMine(buyerId);
    }

    @PutMapping("/{reviewId}")
    @Operation(
        summary = "Sua danh gia cua minh",
        description = "Chi cho phep sua danh gia 1 lan duy nhat theo reviewId."
    )
    @ApiResponse(responseCode = "200", description = "Danh gia sau khi sua")
    @ApiResponse(responseCode = "400", description = "Du lieu danh gia khong hop le")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Khong tim thay danh gia cua ban")
    @ApiResponse(responseCode = "409", description = "Danh gia chi duoc sua toi da 1 lan")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id danh gia") @PathVariable UUID reviewId,
        @Valid @RequestBody UpdateReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return reviews.updateReviewById(buyerId, reviewId, request);
    }

    @PostMapping("/{reviewId}/helpful")
    @Operation(
        summary = "Nhan / bo thich Huu ich cho danh gia",
        description = "Toggle trang thai huu ich cho danh gia. Khong the tu danh dau huu ich bai cua chinh minh."
    )
    @ApiResponse(responseCode = "200", description = "Trang thai huu ich sau thao tac")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Danh gia hoac san pham khong ton tai")
    @ApiResponse(responseCode = "409", description = "Khong the tu danh dau huu ich danh gia cua chinh minh")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewHelpfulResponse toggleHelpful(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id danh gia") @PathVariable UUID reviewId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.toggleHelpful(userId, reviewId);
    }
}
