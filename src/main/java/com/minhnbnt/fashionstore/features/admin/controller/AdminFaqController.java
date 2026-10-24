package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.dto.content.FaqRequest;
import com.minhnbnt.fashionstore.dto.content.FaqResponse;
import com.minhnbnt.fashionstore.features.content.FaqService;
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
@RequestMapping("/api/admin/faqs")
@RequiredArgsConstructor
@Tag(name = "Admin - FAQ", description = "Quản lý câu hỏi thường gặp theo nhóm. Cần role admin.")
public class AdminFaqController {

    private final FaqService faqs;

    @GetMapping
    @Operation(
        operationId = "listAdminFaqs",
        summary = "Danh sách FAQ",
        description = "Mọi câu hỏi kể cả đang ẩn, theo nhóm rồi thứ tự."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách FAQ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<FaqResponse> list() {
        return faqs.listForAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createFaq",
        summary = "Thêm câu hỏi",
        description = "Câu trả lời viết Markdown."
    )
    @ApiResponse(responseCode = "201", description = "Câu hỏi đã thêm")
    @ApiResponse(responseCode = "400", description = "Thiếu trường hoặc quá dài")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public FaqResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FaqRequest request) {
        return faqs.create(adminIdOf(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateFaq",
        summary = "Sửa câu hỏi",
        description = "Thay toàn bộ nhóm, câu hỏi, câu trả lời, thứ tự, hiển thị."
    )
    @ApiResponse(responseCode = "200", description = "Câu hỏi sau khi sửa")
    @ApiResponse(responseCode = "400", description = "Thiếu trường hoặc quá dài")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Câu hỏi không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public FaqResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id câu hỏi") @PathVariable UUID id,
        @Valid @RequestBody FaqRequest request
    ) {
        return faqs.update(adminIdOf(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteFaq",
        summary = "Xóa câu hỏi",
        description = "Xóa hẳn. Muốn tạm ẩn thì bỏ published."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Câu hỏi không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@Parameter(description = "id câu hỏi") @PathVariable UUID id) {
        faqs.delete(id);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
