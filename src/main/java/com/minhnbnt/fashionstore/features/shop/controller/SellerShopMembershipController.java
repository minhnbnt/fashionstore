package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.SellerShopMembershipResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/my-shops")
@RequiredArgsConstructor
@Tag(name = "Seller - Staff & Roles",
        description = "Quản lý nhân viên gian hàng, phân quyền và lịch sử thao tác")
public class SellerShopMembershipController {

    private final ShopStaffService staffService;

    @GetMapping
    @Operation(
        operationId = "getMyShops",
        summary = "Danh sách gian hàng của tôi",
        description = "Trả về danh sách tất cả các gian hàng mà người dùng hiện tại tham gia "
            + "làm chủ gian hàng hoặc nhân viên (bao gồm thông tin vai trò, "
            + "trạng thái thành viên và quyền hạn)."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách gian hàng của người dùng hiện tại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerShopMembershipResponse> getMyShops(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.getMyMemberships(userId);
    }
}
