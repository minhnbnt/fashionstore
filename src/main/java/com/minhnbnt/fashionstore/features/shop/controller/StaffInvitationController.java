package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.InvitationDetailResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Seller - Staff & Roles", description = "Xác nhận và chấp nhận lời mời tham gia quản lý shop")
public class StaffInvitationController {

    private final ShopStaffService staffService;

    @GetMapping("/{token}")
    @Operation(
        operationId = "getInvitation",
        summary = "Chi tiết lời mời theo token",
        description = "Trả về shop, vai trò, email được mời và trạng thái lời mời để trang chấp nhận hiển thị."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết lời mời")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy token lời mời")
    @SecurityRequirement(name = "bearerAuth")
    public InvitationDetailResponse getInvitationDetail(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Token lời mời") @PathVariable String token
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.getInvitationDetail(userId, token);
    }

    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "acceptInvitation",
        summary = "Chấp nhận lời mời tham gia shop",
        description = "Người dùng đã đăng nhập chấp nhận liên kết vào shop theo token lời mời. "
            + "Tài khoản phải đúng người được mời; nhân viên đang bị vô hiệu hóa sẽ được kích hoạt lại."
    )
    @ApiResponse(responseCode = "204", description = "Chấp nhận lời mời thành công")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Lời mời không thuộc tài khoản đang đăng nhập")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy token lời mời")
    @ApiResponse(responseCode = "409", description = "Lời mời đã được chấp nhận trước đó")
    @ApiResponse(responseCode = "410", description = "Lời mời đã hết hạn hoặc đã bị thu hồi")
    @SecurityRequirement(name = "bearerAuth")
    public void acceptInvitation(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Token lời mời") @PathVariable String token
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.acceptInvitation(userId, token);
    }
}
