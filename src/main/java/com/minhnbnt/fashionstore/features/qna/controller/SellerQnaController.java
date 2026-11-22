package com.minhnbnt.fashionstore.features.qna.controller;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.ProductAnswerResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaProductOptionResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaStatsResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.features.qna.ProductQnaService;
import com.minhnbnt.fashionstore.model.ProductQuestionSort;
import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
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
@RequestMapping("/api/seller/qna")
@RequiredArgsConstructor
@Tag(name = "Seller - Q&A", description = "Shop quản lý và trả lời câu hỏi sản phẩm")
public class SellerQnaController {

    private final ProductQnaService qnaService;

    @GetMapping("/questions")
    @Operation(
        operationId = "listSellerQuestions",
        summary = "Danh sách câu hỏi của shop",
        description = "Lấy danh sách câu hỏi theo shop, hỗ trợ lọc theo trạng thái, sản phẩm, khoảng ngày và từ khóa."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách câu hỏi phân trang")
    @ApiResponse(responseCode = "400", description = "Tham số lọc không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_READ trên shop")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<SellerQuestionResponse> listSellerQuestions(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Lọc theo trạng thái câu hỏi")
        @RequestParam(required = false) @Nullable ProductQuestionStatus status,
        @Parameter(description = "Lọc theo id sản phẩm")
        @RequestParam(required = false) @Nullable UUID productId,
        @Parameter(description = "Từ ngày (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
        @Parameter(description = "Đến ngày (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
        @Parameter(description = "Từ khóa tìm kiếm")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Thứ tự sắp xếp (NEWEST | OLDEST)")
        @RequestParam(defaultValue = "NEWEST") ProductQuestionSort sort,
        @Parameter(description = "Số trang (bắt đầu từ 0)")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số lượng mục mỗi trang")
        @RequestParam(defaultValue = "20") int size
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.listForShop(userId, shopId, status, productId, from, to, q, sort, page, size);
    }

    @GetMapping("/questions/{questionId}")
    @Operation(
        operationId = "getSellerQuestion",
        summary = "Chi tiết câu hỏi của shop",
        description = "Xem chi tiết một câu hỏi và toàn bộ danh sách câu trả lời."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết câu hỏi")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_READ trên shop")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi")
    @SecurityRequirement(name = "bearerAuth")
    public SellerQuestionResponse getSellerQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.getShopQuestion(userId, questionId);
    }

    @GetMapping("/products")
    @Operation(
        operationId = "listSellerQnaProducts",
        summary = "Danh sách sản phẩm có câu hỏi của shop",
        description = "Trả về danh sách sản phẩm kèm số lượng câu hỏi để làm bộ lọc."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_READ trên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerQnaProductOptionResponse> listSellerQnaProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.listShopQnaProducts(userId, shopId);
    }

    @GetMapping("/stats")
    @Operation(
        operationId = "getSellerQnaStats",
        summary = "Thống kê Q&A của shop",
        description = "Tổng hợp số câu hỏi, câu hỏi chưa trả lời, đã trả lời, đã đóng và tỷ lệ phản hồi."
    )
    @ApiResponse(responseCode = "200", description = "Thống kê Q&A")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_READ trên shop")
    @SecurityRequirement(name = "bearerAuth")
    public SellerQnaStatsResponse getSellerQnaStats(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.shopQnaStats(userId, shopId);
    }

    @PostMapping("/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createSellerAnswer",
        summary = "Shop trả lời câu hỏi",
        description = "Thêm câu trả lời chính thức từ shop cho câu hỏi sản phẩm."
    )
    @ApiResponse(responseCode = "201", description = "Câu hỏi sau khi trả lời")
    @ApiResponse(responseCode = "400", description = "Nội dung câu trả lời không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_REPLY trên shop")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public SellerQuestionResponse createSellerAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId,
        @Valid @RequestBody CreateAnswerRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.createShopAnswer(userId, questionId, request);
    }

    @PutMapping("/answers/{answerId}")
    @Operation(
        operationId = "updateSellerAnswer",
        summary = "Shop cập nhật câu trả lời",
        description = "Chỉnh sửa nội dung câu trả lời của shop."
    )
    @ApiResponse(responseCode = "200", description = "Câu trả lời sau khi cập nhật")
    @ApiResponse(responseCode = "400", description = "Nội dung câu trả lời không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(
        responseCode = "403",
        description = "Không có quyền REVIEW_REPLY trên shop hoặc câu trả lời không thuộc shop"
    )
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu trả lời")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public ProductAnswerResponse updateSellerAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu trả lời") @PathVariable UUID answerId,
        @Valid @RequestBody UpdateAnswerRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.updateShopAnswer(userId, answerId, request);
    }

    @DeleteMapping("/answers/{answerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteSellerAnswer",
        summary = "Shop xóa câu trả lời",
        description = "Xóa câu trả lời của shop."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa câu trả lời")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(
        responseCode = "403",
        description = "Không có quyền REVIEW_REPLY trên shop hoặc câu trả lời không thuộc shop"
    )
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu trả lời")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteSellerAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu trả lời") @PathVariable UUID answerId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        qnaService.deleteShopAnswer(userId, answerId);
    }

    @PatchMapping("/questions/{questionId}/close")
    @Operation(
        operationId = "closeSellerQuestion",
        summary = "Shop đóng câu hỏi",
        description = "Đóng câu hỏi để không nhận thêm câu trả lời từ buyer hoặc shop."
    )
    @ApiResponse(responseCode = "200", description = "Câu hỏi sau khi đóng")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_REPLY trên shop")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi")
    @SecurityRequirement(name = "bearerAuth")
    public SellerQuestionResponse closeSellerQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.closeQuestion(userId, questionId);
    }

    @PatchMapping("/questions/{questionId}/reopen")
    @Operation(
        operationId = "reopenSellerQuestion",
        summary = "Shop mở lại câu hỏi",
        description = "Mở lại câu hỏi đã đóng để tiếp tục thảo luận."
    )
    @ApiResponse(responseCode = "200", description = "Câu hỏi sau khi mở lại")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không có quyền REVIEW_REPLY trên shop")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi")
    @SecurityRequirement(name = "bearerAuth")
    public SellerQuestionResponse reopenSellerQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.reopenQuestion(userId, questionId);
    }
}
