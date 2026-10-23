package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.features.admin.service.AdminProductService;

import com.minhnbnt.fashionstore.dto.admin.AdminProductDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.admin.BanProductRequest;
import com.minhnbnt.fashionstore.dto.admin.DismissReportsRequest;
import com.minhnbnt.fashionstore.dto.admin.DismissReportsResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.model.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin - Products", description = "Kiểm duyệt sản phẩm toàn sàn, gỡ sản phẩm vi phạm. Cần role admin.")
public class AdminProductController {

    private final AdminProductService adminProducts;

    @GetMapping
    @Operation(
        operationId = "listAdminProducts", summary = "Danh sách sản phẩm toàn sàn",
        description = "Lọc theo trạng thái, shop + tìm theo tên/slug sản phẩm, tên shop, SKU. Mới cập nhật lên đầu."
    )
    @ApiResponse(responseCode = "200", description = "Trang kết quả")
    @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc phân trang sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<AdminProductResponse> list(
        @Parameter(description = "Trạng thái, bỏ trống = tất cả", example = "BANNED")
        @RequestParam(required = false) @Nullable ProductStatus status,
        @Parameter(description = "Chỉ lấy sản phẩm của shop này")
        @RequestParam(required = false) @Nullable UUID shopId,
        @Parameter(description = "Từ khóa: tên/slug sản phẩm, tên shop hoặc SKU")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "true = hàng đợi báo cáo: chỉ sản phẩm có báo cáo đang mở, nhiều báo cáo nhất trước")
        @RequestParam(defaultValue = "false") boolean reported,
        @Parameter(description = "Trang, đếm từ 0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Số sản phẩm mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return adminProducts.search(status, shopId, q, reported, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getAdminProduct",
        summary = "Chi tiết sản phẩm",
        description = "Thông tin + shop + biến thể + ảnh + lịch sử kiểm duyệt, kể cả sản phẩm đã ẩn/gỡ/xóa."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết sản phẩm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public AdminProductDetailResponse detail(@Parameter(description = "id sản phẩm") @PathVariable UUID id) {
        return adminProducts.getDetail(id);
    }

    @PatchMapping("/{id}/ban")
    @Operation(
        operationId = "banProduct", summary = "Gỡ sản phẩm vi phạm",
        description = "ACTIVE/HIDDEN → BANNED kèm loại vi phạm + lý do. Sản phẩm biến mất khỏi tìm kiếm, "
            + "trang chi tiết và checkout ngay; shop thấy lý do và không tự đăng bán lại được. "
            + "Đơn đang xử lý vẫn tiếp tục."
    )
    @ApiResponse(responseCode = "200", description = "Sản phẩm đã gỡ")
    @ApiResponse(responseCode = "400", description = "Thiếu loại vi phạm/lý do hoặc không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sản phẩm đã bị gỡ hoặc đã xóa")
    @SecurityRequirement(name = "bearerAuth")
    public AdminProductResponse ban(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id sản phẩm") @PathVariable UUID id,
        @Valid @RequestBody BanProductRequest request
    ) {
        return adminProducts.ban(adminIdOf(jwt), id, request.violationType(), request.reason());
    }

    @PatchMapping("/{id}/restore")
    @Operation(
        operationId = "restoreProduct",
        summary = "Khôi phục sản phẩm bị gỡ",
        description = "BANNED → HIDDEN, xóa loại vi phạm + lý do. Shop kiểm tra lại rồi tự đăng bán."
    )
    @ApiResponse(responseCode = "200", description = "Sản phẩm đã khôi phục về trạng thái ẩn")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sản phẩm không ở trạng thái BANNED")
    @SecurityRequirement(name = "bearerAuth")
    public AdminProductResponse restore(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id sản phẩm") @PathVariable UUID id
    ) {
        return adminProducts.restore(adminIdOf(jwt), id);
    }

    @PatchMapping("/{id}/reports/dismiss")
    @Operation(
        operationId = "dismissProductReports",
        summary = "Bác bỏ báo cáo",
        description = "Đóng mọi báo cáo đang mở của sản phẩm là DISMISSED (không vi phạm). "
            + "Sản phẩm giữ nguyên trạng thái."
    )
    @ApiResponse(responseCode = "200", description = "Số báo cáo đã đóng")
    @ApiResponse(responseCode = "400", description = "Ghi chú quá dài")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sản phẩm không có báo cáo đang mở")
    @SecurityRequirement(name = "bearerAuth")
    public DismissReportsResponse dismissReports(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id sản phẩm") @PathVariable UUID id,
        @Valid @RequestBody(required = false) @Nullable DismissReportsRequest request
    ) {
        String note = null;
        if (request != null) {
            note = request.note();
        }
        return adminProducts.dismissReports(adminIdOf(jwt), id, note);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
