package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.features.admin.service.AdminUserService;

import com.minhnbnt.fashionstore.dto.admin.AdminUserDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.admin.ModerationReasonRequest;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Quản lý tài khoản khách hàng/seller. Cần role admin.")
public class AdminUserController {

    private final AdminUserService adminUsers;

    @GetMapping
    @Operation(
        operationId = "listAdminUsers",
        summary = "Danh sách tài khoản",
        description = "Lọc trạng thái/role, tìm theo email hoặc tên. Mới tạo trước."
    )
    @ApiResponse(responseCode = "200", description = "Trang kết quả")
    @ApiResponse(responseCode = "400", description = "Tham số phân trang sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<AdminUserResponse> list(
        @Parameter(description = "Trạng thái, bỏ trống = tất cả", example = "LOCKED")
        @RequestParam(required = false) @Nullable AccountStatus status,
        @Parameter(description = "Role, bỏ trống = tất cả", example = "CUSTOMER")
        @RequestParam(required = false) @Nullable UserRole role,
        @Parameter(description = "Từ khóa: email hoặc tên hiển thị")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Trang, đếm từ 0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Số tài khoản mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return adminUsers.search(status, role, q, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getAdminUser",
        summary = "Chi tiết tài khoản",
        description = "Profile + shop tham gia + số đơn đã đặt + lịch sử khóa."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết tài khoản")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Tài khoản không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public AdminUserDetailResponse detail(@Parameter(description = "id user") @PathVariable UUID id) {
        return adminUsers.getDetail(id);
    }

    @PatchMapping("/{id}/lock")
    @Operation(
        operationId = "lockUser", summary = "Khóa tài khoản",
        description = "ACTIVE → LOCKED. Chặn mọi API ngay lập tức, ban ở GoTrue, tạm khóa shop mà user làm OWNER. "
            + "Không khóa được admin hoặc chính mình."
    )
    @ApiResponse(responseCode = "200", description = "Tài khoản đã khóa")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc lý do không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Tài khoản không tồn tại")
    @ApiResponse(responseCode = "409", description = "Đã khóa, là admin, hoặc tự khóa mình")
    @ApiResponse(responseCode = "502", description = "Cập nhật GoTrue thất bại, không có gì thay đổi")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse lock(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id user") @PathVariable UUID id,
        @Valid @RequestBody ModerationReasonRequest request
    ) {
        return adminUsers.lock(adminIdOf(jwt), id, request.reason());
    }

    @PatchMapping("/{id}/unlock")
    @Operation(
        operationId = "unlockUser", summary = "Mở khóa tài khoản",
        description = "LOCKED → ACTIVE, gỡ ban GoTrue. Shop bị tạm khóa kèm theo không tự mở, admin mở riêng."
    )
    @ApiResponse(responseCode = "200", description = "Tài khoản hoạt động lại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Tài khoản không tồn tại")
    @ApiResponse(responseCode = "409", description = "Tài khoản không bị khóa")
    @ApiResponse(responseCode = "502", description = "Cập nhật GoTrue thất bại, không có gì thay đổi")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse unlock(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id user") @PathVariable UUID id
    ) {
        return adminUsers.unlock(adminIdOf(jwt), id);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
