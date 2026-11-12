package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
}

