package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.BannedKeywordCreatedResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordResponse;
import com.minhnbnt.fashionstore.dto.admin.CreateBannedKeywordRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/banned-keywords")
@RequiredArgsConstructor
@Tag(name = "Admin - Banned Keywords", description = "Từ khóa cấm để hệ thống tự gắn cờ sản phẩm. Cần role admin.")
public class AdminBannedKeywordController {

    private final AdminProductService adminProducts;

    @GetMapping
    @Operation(operationId = "listBannedKeywords", summary = "Danh sách từ khóa cấm", description = "Mới thêm lên đầu.")
    @ApiResponse(responseCode = "200", description = "Danh sách từ khóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<BannedKeywordResponse> list() {
        return adminProducts.listKeywords();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createBannedKeyword",
        summary = "Thêm từ khóa cấm",
        description = "Lưu dạng chữ thường. Quét ngay sản phẩm đang bán/đang ẩn chứa từ khóa (khớp nguyên từ) "
            + "và gắn cờ hệ thống vào hàng đợi báo cáo; trả số sản phẩm vừa bị gắn cờ. Không tự gỡ sản phẩm."
    )
    @ApiResponse(responseCode = "201", description = "Đã thêm từ khóa")
    @ApiResponse(responseCode = "400", description = "Từ khóa trống, quá ngắn/dài hoặc thiếu loại vi phạm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "409", description = "Từ khóa đã tồn tại (không phân biệt hoa thường)")
    @SecurityRequirement(name = "bearerAuth")
    public BannedKeywordCreatedResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateBannedKeywordRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return adminProducts.addKeyword(UUID.fromString(subject), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteBannedKeyword",
        summary = "Xóa từ khóa cấm",
        description = "Không đóng các cờ đã tạo trước đó, admin vẫn xử lý nốt trong hàng đợi."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Từ khóa không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@Parameter(description = "id từ khóa") @PathVariable UUID id) {
        adminProducts.deleteKeyword(id);
    }
}
