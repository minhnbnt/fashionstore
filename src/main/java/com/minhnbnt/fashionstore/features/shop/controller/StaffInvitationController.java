package com.minhnbnt.fashionstore.features.shop.controller;

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

    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Chấp nhận lời mời tham gia shop", description = "Người dùng đã đăng nhập chấp nhận liên kết vào shop theo token lời mời.")
    @ApiResponse(responseCode = "204", description = "Chấp nhận lời mời thành công")
    @ApiResponse(responseCode = "400", description = "Lời mời đã hết hạn")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy token lời mời")
    @ApiResponse(responseCode = "409", description = "Lời mời đã được chấp nhận trước đó")
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
