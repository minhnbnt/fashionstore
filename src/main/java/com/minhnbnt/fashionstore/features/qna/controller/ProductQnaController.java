package com.minhnbnt.fashionstore.features.qna.controller;

import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.CreateQuestionRequest;
import com.minhnbnt.fashionstore.dto.qna.ProductAnswerResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionPageResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.UpdateQuestionRequest;
import com.minhnbnt.fashionstore.features.qna.ProductQnaService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/questions")
@RequiredArgsConstructor
@Tag(name = "Product Q&A", description = "Hỏi đáp về sản phẩm cho khách hàng")
public class ProductQnaController {

    private final ProductQnaService qnaService;

    @GetMapping
    @Operation(
        operationId = "listProductQuestions",
        summary = "Danh sách câu hỏi sản phẩm",
        description = "Công khai (nhận JWT nếu đã đăng nhập để đánh dấu mine/canEdit). Hỗ trợ tìm kiếm từ khóa."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách câu hỏi và phân trang")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại hoặc đã bị ẩn")
    public ProductQuestionPageResponse listProductQuestions(
        @AuthenticationPrincipal @Nullable Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Từ khóa tìm kiếm trong nội dung câu hỏi")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Số trang (bắt đầu từ 0)")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số lượng mục mỗi trang")
        @RequestParam(defaultValue = "10") int size
    ) {
        var currentUserId = extractUserId(jwt);
        return qnaService.listForProduct(productId, currentUserId, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createProductQuestion",
        summary = "Đặt câu hỏi về sản phẩm",
        description = "Yêu cầu đăng nhập. Người dùng đặt câu hỏi mới về sản phẩm."
    )
    @ApiResponse(responseCode = "201", description = "Câu hỏi vừa tạo")
    @ApiResponse(responseCode = "400", description = "Nội dung không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại hoặc đã bị ẩn")
    @SecurityRequirement(name = "bearerAuth")
    public ProductQuestionResponse createProductQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Valid @RequestBody CreateQuestionRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.createQuestion(userId, productId, request);
    }

    @PutMapping("/{questionId}")
    @Operation(
        operationId = "updateProductQuestion",
        summary = "Chỉnh sửa câu hỏi",
        description = "Chỉ người đặt câu hỏi mới được sửa và chỉ sửa khi chưa có câu trả lời nào."
    )
    @ApiResponse(responseCode = "200", description = "Câu hỏi sau khi chỉnh sửa")
    @ApiResponse(responseCode = "400", description = "Nội dung không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không phải người tạo câu hỏi")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi hoặc sản phẩm")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã có câu trả lời hoặc đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public ProductQuestionResponse updateProductQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId,
        @Valid @RequestBody UpdateQuestionRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.updateQuestion(userId, productId, questionId, request);
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteProductQuestion",
        summary = "Xóa câu hỏi",
        description = "Chỉ người đặt câu hỏi mới được xóa và chỉ xóa khi chưa có câu trả lời nào."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa câu hỏi")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không phải người tạo câu hỏi")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi hoặc sản phẩm")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã có câu trả lời hoặc đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteProductQuestion(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        qnaService.deleteQuestion(userId, productId, questionId);
    }

    @PostMapping("/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createProductAnswer",
        summary = "Trả lời câu hỏi (Verified Buyer)",
        description = "Chỉ khách hàng đã nhận hàng sản phẩm này mới được trả lời."
    )
    @ApiResponse(responseCode = "201", description = "Câu trả lời vừa tạo")
    @ApiResponse(responseCode = "400", description = "Nội dung không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Chưa mua và nhận sản phẩm này")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi hoặc sản phẩm")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public ProductAnswerResponse createProductAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId,
        @Valid @RequestBody CreateAnswerRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.createBuyerAnswer(userId, productId, questionId, request);
    }

    @PutMapping("/{questionId}/answers/{answerId}")
    @Operation(
        operationId = "updateProductAnswer",
        summary = "Chỉnh sửa câu trả lời",
        description = "Chỉ người đã trả lời mới được sửa câu trả lời của mình."
    )
    @ApiResponse(responseCode = "200", description = "Câu trả lời sau khi chỉnh sửa")
    @ApiResponse(responseCode = "400", description = "Nội dung không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không phải người viết câu trả lời")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi hoặc câu trả lời")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public ProductAnswerResponse updateProductAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId,
        @Parameter(description = "Id câu trả lời") @PathVariable UUID answerId,
        @Valid @RequestBody UpdateAnswerRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return qnaService.updateBuyerAnswer(userId, productId, questionId, answerId, request);
    }

    @DeleteMapping("/{questionId}/answers/{answerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteProductAnswer",
        summary = "Xóa câu trả lời",
        description = "Chỉ người đã trả lời mới được xóa câu trả lời của mình."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa câu trả lời")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    @ApiResponse(responseCode = "403", description = "Không phải người viết câu trả lời")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi hoặc câu trả lời")
    @ApiResponse(responseCode = "409", description = "Câu hỏi đã đóng")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteProductAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id sản phẩm") @PathVariable UUID productId,
        @Parameter(description = "Id câu hỏi") @PathVariable UUID questionId,
        @Parameter(description = "Id câu trả lời") @PathVariable UUID answerId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        qnaService.deleteBuyerAnswer(userId, productId, questionId, answerId);
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
