package com.minhnbnt.fashionstore.features.voucher.controller;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.voucher.CreateShopVoucherRequest;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherDetailResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.ToggleVoucherActiveRequest;
import com.minhnbnt.fashionstore.dto.voucher.UpdateShopVoucherRequest;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/seller/shops/{shopId}/vouchers")
@RequiredArgsConstructor
@Tag(
    name = "Seller Vouchers",
    description = "Quản lý mã giảm giá của gian hàng tại Kênh Người Bán"
)
public class SellerVoucherController {

    private final VoucherService vouchers;

    @GetMapping
    @Operation(
        operationId = "listSellerVouchers",
        summary = "Danh sách mã giảm giá của gian hàng",
        description = "Liệt kê mã giảm giá của shop "
            + "kèm bộ lọc theo trạng thái và từ khóa tìm kiếm."
    )
    @ApiResponse(responseCode = "200", description = "Trang danh sách mã giảm giá")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_READ hoặc VOUCHER_WRITE")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<SellerVoucherResponse> listSellerVouchers(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Parameter(description = "Lọc theo trạng thái: ONGOING, UPCOMING, PAUSED, DEPLETED, EXPIRED")
        @RequestParam(required = false) @Nullable String status,
        @Parameter(description = "Tìm kiếm theo mã code hoặc tên chương trình")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Trang hiện tại (từ 0)")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số phần tử mỗi trang")
        @RequestParam(defaultValue = "20") int size
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return vouchers.listForSeller(userId, shopId, status, q, page, size);
    }

    @GetMapping("/{voucherId}")
    @Operation(
        operationId = "getSellerVoucher",
        summary = "Chi tiết mã giảm giá của gian hàng",
        description = "Xem chi tiết mã giảm giá kèm danh sách sản phẩm chỉ định nếu có."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết mã giảm giá")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_READ hoặc VOUCHER_WRITE")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mã giảm giá")
    @SecurityRequirement(name = "bearerAuth")
    public SellerVoucherDetailResponse getSellerVoucher(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Parameter(description = "ID mã giảm giá") @PathVariable UUID voucherId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return vouchers.getForSeller(userId, shopId, voucherId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createSellerVoucher",
        summary = "Tạo mã giảm giá mới cho gian hàng",
        description = "Tạo mã giảm giá mới. "
            + "Yêu cầu gian hàng đã được duyệt và quyền VOUCHER_WRITE."
    )
    @ApiResponse(responseCode = "201", description = "Mã giảm giá vừa tạo")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_WRITE hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "409", description = "Mã code đã tồn tại trong gian hàng")
    @SecurityRequirement(name = "bearerAuth")
    public SellerVoucherDetailResponse createSellerVoucher(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Valid @RequestBody CreateShopVoucherRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return vouchers.create(userId, shopId, request);
    }

    @PutMapping("/{voucherId}")
    @Operation(
        operationId = "updateSellerVoucher",
        summary = "Cập nhật mã giảm giá của gian hàng",
        description = "Cập nhật thông tin mã giảm giá. "
            + "Nếu mã đã có lượt dùng thì khóa các điều khoản giảm giá cốt lõi."
    )
    @ApiResponse(responseCode = "200", description = "Mã giảm giá sau khi cập nhật")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_WRITE")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mã giảm giá")
    @ApiResponse(
        responseCode = "409",
        description = "Trùng mã code hoặc sửa trường bị khóa khi đã có lượt dùng"
    )
    @SecurityRequirement(name = "bearerAuth")
    public SellerVoucherDetailResponse updateSellerVoucher(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Parameter(description = "ID mã giảm giá") @PathVariable UUID voucherId,
        @Valid @RequestBody UpdateShopVoucherRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return vouchers.update(userId, shopId, voucherId, request);
    }

    @PatchMapping("/{voucherId}/active")
    @Operation(
        operationId = "toggleSellerVoucherActive",
        summary = "Bật hoặc tạm dừng mã giảm giá",
        description = "Thay đổi nhanh trạng thái kích hoạt (active) của mã giảm giá."
    )
    @ApiResponse(responseCode = "200", description = "Mã giảm giá sau khi đổi trạng thái")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_WRITE")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mã giảm giá")
    @SecurityRequirement(name = "bearerAuth")
    public SellerVoucherResponse toggleSellerVoucherActive(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Parameter(description = "ID mã giảm giá") @PathVariable UUID voucherId,
        @Valid @RequestBody ToggleVoucherActiveRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return vouchers.toggleActive(userId, shopId, voucherId, request.active());
    }

    @DeleteMapping("/{voucherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteSellerVoucher",
        summary = "Xóa mã giảm giá chưa sử dụng",
        description = "Chỉ cho phép xóa cứng khi mã giảm giá "
            + "chưa có lượt sử dụng nào (usedCount == 0)."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa mã giảm giá")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền VOUCHER_WRITE")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy mã giảm giá")
    @ApiResponse(responseCode = "409", description = "Không thể xóa mã giảm giá đã có lượt sử dụng")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteSellerVoucher(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID gian hàng") @PathVariable UUID shopId,
        @Parameter(description = "ID mã giảm giá") @PathVariable UUID voucherId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        vouchers.delete(userId, shopId, voucherId);
    }
}
