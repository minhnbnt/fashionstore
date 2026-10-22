package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.AdminShopDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.ModerationReasonRequest;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.model.ShopStatus;
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
@RequestMapping("/api/admin/shops")
@RequiredArgsConstructor
@Tag(name = "Admin - Shops", description = "Duyệt shop mới và kiểm duyệt shop đang hoạt động. Cần role admin.")
public class AdminShopController {

    private final AdminShopService adminShops;

    @GetMapping
    @Operation(
        operationId = "listAdminShops", summary = "Danh sách shop",
        description = "Lọc theo trạng thái + tìm theo tên/slug/email owner. "
            + "PENDING xếp ai nộp trước lên trước (hàng đợi duyệt), trạng thái khác xếp mới cập nhật trước."
    )
    @ApiResponse(responseCode = "200", description = "Trang kết quả")
    @ApiResponse(responseCode = "400", description = "Tham số phân trang sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<AdminShopResponse> list(
        @Parameter(description = "Trạng thái, bỏ trống = tất cả", example = "PENDING")
        @RequestParam(required = false) @Nullable ShopStatus status,
        @Parameter(description = "Từ khóa: tên, slug hoặc email owner")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Trang, đếm từ 0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Số shop mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return adminShops.search(status, q, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getAdminShop",
        summary = "Chi tiết shop",
        description = "Hồ sơ + owner + số sản phẩm/đơn + lịch sử trạng thái."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết shop")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public AdminShopDetailResponse detail(@Parameter(description = "id shop") @PathVariable UUID id) {
        return adminShops.getDetail(id);
    }

    @PatchMapping("/{id}/approve")
    @Operation(
        operationId = "approveShop", summary = "Duyệt shop",
        description = "PENDING → APPROVED. Owner được nâng role SHOP (DB + GoTrue app_metadata)."
    )
    @ApiResponse(responseCode = "200", description = "Shop đã duyệt")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái PENDING")
    @ApiResponse(responseCode = "502", description = "Cập nhật GoTrue thất bại, không có gì thay đổi")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse approve(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        return adminShops.approve(adminIdOf(jwt), id);
    }

    @PatchMapping("/{id}/reject")
    @Operation(
        operationId = "rejectShop",
        summary = "Từ chối shop",
        description = "PENDING → REJECTED. Owner thấy lý do và có thể gửi duyệt lại."
    )
    @ApiResponse(responseCode = "200", description = "Shop đã từ chối")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc lý do không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái PENDING")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse reject(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id,
        @Valid @RequestBody ModerationReasonRequest request
    ) {
        return adminShops.reject(adminIdOf(jwt), id, request.reason());
    }

    @PatchMapping("/{id}/suspend")
    @Operation(
        operationId = "suspendShop", summary = "Tạm khóa shop",
        description = "APPROVED → SUSPENDED. Shop ẩn khỏi sàn, không nhận đơn mới; đơn đang có vẫn xử lý được."
    )
    @ApiResponse(responseCode = "200", description = "Shop đã tạm khóa")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc lý do không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái APPROVED")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse suspend(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id,
        @Valid @RequestBody ModerationReasonRequest request
    ) {
        return adminShops.suspend(adminIdOf(jwt), id, request.reason());
    }

    @PatchMapping("/{id}/reinstate")
    @Operation(
        operationId = "reinstateShop",
        summary = "Mở lại shop",
        description = "SUSPENDED → APPROVED, xóa lý do tạm khóa."
    )
    @ApiResponse(responseCode = "200", description = "Shop hoạt động lại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái SUSPENDED")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse reinstate(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        return adminShops.reinstate(adminIdOf(jwt), id);
    }

    @PatchMapping("/{id}/trust")
    @Operation(
        operationId = "toggleShopTrust",
        summary = "Bật/tắt cờ Shop uy tín",
        description = "Shop uy tín được tự động duyệt sản phẩm ngay khi đăng bán."
    )
    @ApiResponse(responseCode = "200", description = "Đã cập nhật trạng thái uy tín")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse toggleTrust(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id,
        @RequestParam boolean trusted
    ) {
        return adminShops.setTrusted(adminIdOf(jwt), id, trusted);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
