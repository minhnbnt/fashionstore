package com.minhnbnt.fashionstore.features.review.controller;

import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewReplyTemplateResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewSummaryResponse;
import com.minhnbnt.fashionstore.dto.review.SaveReviewReplyTemplateRequest;
import com.minhnbnt.fashionstore.dto.review.SellerReviewProductOptionResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewTrendResponse;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
        description = "Hỗ trợ lọc theo trạng thái chưa phản hồi (unreplied=true), số sao (1-5) và sản phẩm."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách đánh giá")
    @ApiResponse(responseCode = "400", description = "Tham số lọc không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerReviewResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Chỉ xem đánh giá chưa phản hồi")
        @RequestParam(defaultValue = "false") boolean unreplied,
        @Parameter(description = "Lọc theo số sao (1-5)")
        @RequestParam(required = false) @Nullable Integer stars,
        @Parameter(description = "Lọc theo id sản phẩm")
        @RequestParam(required = false) @Nullable UUID productId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.listForShop(userId, shopId, unreplied, stars, productId);
    }

    @GetMapping("/products")
    @Operation(
        summary = "Danh sách sản phẩm cho bộ lọc đánh giá",
        description = "Trả danh sách sản phẩm thuộc shop (bao gồm sản phẩm đã xóa nhưng từng có đánh giá) để CSKH/Chủ shop lọc đánh giá."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách tùy chọn sản phẩm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerReviewProductOptionResponse> products(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.listShopReviewProducts(userId, shopId);
    }

    @GetMapping("/trend")
    @Operation(
        summary = "Xu hướng đánh giá của shop theo thời gian",
        description = "Tổng hợp số lượng đánh giá và điểm sao trung bình theo ngày (day), tuần (week), hoặc tháng (month)."
    )
    @ApiResponse(responseCode = "200", description = "Chuỗi dữ liệu xu hướng đánh giá")
    @ApiResponse(responseCode = "400", description = "Chu kỳ hoặc khoảng ngày không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public SellerReviewTrendResponse trend(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Chu kỳ gom nhóm: day, week, month")
        @RequestParam(defaultValue = "day") String period,
        @Parameter(description = "Từ ngày (YYYY-MM-DD, múi giờ Việt Nam)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
        @Parameter(description = "Đến ngày (YYYY-MM-DD, múi giờ Việt Nam)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.shopReviewTrend(userId, shopId, period, from, to);
    }

    @GetMapping("/templates")
    @Operation(
        summary = "Danh sách mẫu phản hồi nhanh",
        description = "Trả toàn bộ mẫu phản hồi nhanh văn bản thuần của shop."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách mẫu phản hồi nhanh")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<ReviewReplyTemplateResponse> listTemplates(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.listReplyTemplates(userId, shopId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo mẫu phản hồi nhanh",
        description = "Lưu mẫu phản hồi nhanh văn bản thuần mới cho shop (tối đa 50 mẫu, tiêu đề không trùng)."
    )
    @ApiResponse(responseCode = "201", description = "Mẫu vừa tạo")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
    @ApiResponse(responseCode = "409", description = "Trùng tiêu đề hoặc đã đạt giới hạn 50 mẫu")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewReplyTemplateResponse createTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Valid @RequestBody SaveReviewReplyTemplateRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.createReplyTemplate(userId, shopId, request);
    }

    @PutMapping("/templates/{templateId}")
    @Operation(
        summary = "Cập nhật mẫu phản hồi nhanh",
        description = "Cập nhật tiêu đề và nội dung văn bản thuần của mẫu phản hồi nhanh."
    )
    @ApiResponse(responseCode = "200", description = "Mẫu sau khi cập nhật")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mẫu")
    @ApiResponse(responseCode = "409", description = "Tiêu đề đã tồn tại trong shop")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewReplyTemplateResponse updateTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id mẫu") @PathVariable UUID templateId,
        @Valid @RequestBody SaveReviewReplyTemplateRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.updateReplyTemplate(userId, templateId, request);
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Xóa mẫu phản hồi nhanh",
        description = "Xóa vĩnh viễn mẫu phản hồi nhanh của shop."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa mẫu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mẫu")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id mẫu") @PathVariable UUID templateId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        reviews.deleteReplyTemplate(userId, templateId);
    }

    @GetMapping("/summary")
    @Operation(
        summary = "Thống kê đánh giá của shop",
        description = "Trả điểm trung bình, tổng số đánh giá và phân bố 5 -> 1 sao cho toàn bộ sản phẩm của shop."
    )
    @ApiResponse(responseCode = "200", description = "Thống kê đánh giá")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_READ")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewSummaryResponse summary(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return reviews.shopReviewSummary(userId, shopId);
    }

    @PatchMapping("/{id}/reply")
    @Operation(
        summary = "Phản hồi đánh giá",
        description = "Seller chỉ được phản hồi một lần duy nhất cho mỗi đánh giá."
    )
    @ApiResponse(responseCode = "200", description = "Đánh giá sau khi phản hồi")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
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
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVIEW_REPLY")
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
