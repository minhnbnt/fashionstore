package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/reviews")
@RequiredArgsConstructor
@Tag(name = "Seller - Reviews", description = "Seller quản lý đánh giá của khách và phản hồi")
public class SellerReviewController {

    private final ReviewService reviews;

    @GetMapping
    @Operation(
        summary = "Xem đánh giá của shop",
        description = "Có thể lọc các đánh giá chưa phản hồi (unreplied=true)."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách đánh giá")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerReviewResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Chỉ xem đánh giá chưa phản hồi")
        @RequestParam(defaultValue = "false") boolean unreplied
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.listForShop(userId, shopId, unreplied);
    }

    @PatchMapping("/{id}/reply")
    @Operation(
        summary = "Phản hồi đánh giá",
        description = "Seller chỉ được phản hồi một lần duy nhất cho mỗi đánh giá."
    )
    @ApiResponse(responseCode = "200", description = "Đánh giá sau khi phản hồi")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
    @ApiResponse(responseCode = "404", description = "Đánh giá không tồn tại")
    @ApiResponse(responseCode = "409", description = "Đánh giá đã được phản hồi trước đó")
    @SecurityRequirement(name = "bearerAuth")
    public SellerReviewResponse reply(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id đánh giá") @PathVariable UUID id,
        @Valid @RequestBody ReplyReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.reply(userId, id, request);
    }

    @PostMapping("/{id}/report")
    @Operation(
        summary = "Báo cáo đánh giá vi phạm",
        description = "Báo cáo nội dung vi phạm tiêu chuẩn cộng đồng để ban quản trị xử lý."
    )
    @ApiResponse(responseCode = "200", description = "Đã gửi báo cáo")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
    @ApiResponse(responseCode = "404", description = "Đánh giá không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public SellerReviewResponse report(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id đánh giá") @PathVariable UUID id,
        @Valid @RequestBody ReportReviewRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.report(userId, id, request);
    }
}
