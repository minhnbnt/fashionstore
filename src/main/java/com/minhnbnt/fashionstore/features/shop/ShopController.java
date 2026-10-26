package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Tag(name = "Shops", description = "Đăng ký và quản lý shop của seller")
public class ShopController {

    private final ShopService shops;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký shop mới", description = "Shop tạo ở trạng thái PENDING, admin duyệt mới bán được.")
    @ApiResponse(responseCode = "201", description = "Shop vừa tạo")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "409", description = "Slug đã có người dùng")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse register(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateShopRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return shops.register(userId, request);
    }

    @GetMapping("/mine")
    @Operation(summary = "Shop của tôi")
    @ApiResponse(responseCode = "200", description = "Danh sách shop tôi làm OWNER/STAFF")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<ShopResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return shops.getMine(userId);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Xem shop public", description = "Chỉ shop APPROVED mới xem được.")
    @ApiResponse(responseCode = "200", description = "Thông tin shop")
    @ApiResponse(responseCode = "404", description = "Không có hoặc chưa duyệt")
    public ShopResponse bySlug(
        @Parameter(description = "Slug shop", example = "shop-an") @PathVariable String slug
    ) {
        return shops.getBySlug(slug);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin cơ bản shop", description = "OWNER/STAFF của shop. Field null = giữ nguyên.")
    @ApiResponse(responseCode = "200", description = "Shop sau update")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id,
        @Valid @RequestBody UpdateShopRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return shops.update(userId, id, request);
    }

    @PostMapping("/{id}/resubmit")
    @Operation(
        operationId = "resubmitShop", summary = "Gửi duyệt lại shop bị từ chối",
        description = "Chỉ OWNER. REJECTED → PENDING, xóa lý do cũ, xếp cuối hàng đợi duyệt. "
            + "Sửa hồ sơ bằng PUT /api/shops/{id} trước khi gửi lại."
    )
    @ApiResponse(responseCode = "200", description = "Shop đã quay về PENDING")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải OWNER của shop")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái REJECTED")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse resubmit(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var ownerId = UUID.fromString(subject);

        return shops.resubmit(ownerId, id);
    }

    @PatchMapping("/{id}/profile")
    @Operation(
        summary = "Cập nhật hồ sơ mở rộng của shop",
        description = "Cập nhật thông tin kho, SLA, tài khoản ngân hàng, giờ làm việc (OWNER/STAFF)."
    )
    @ApiResponse(responseCode = "200", description = "Shop sau khi cập nhật hồ sơ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id,
        @Valid @RequestBody UpdateShopProfileRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return shops.updateProfile(userId, id, request);
    }

    @PostMapping("/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Theo dõi shop", description = "Người dùng nhấn theo dõi shop.")
    @ApiResponse(responseCode = "204", description = "Đã theo dõi thành công")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void follow(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        shops.follow(userId, id);
    }

    @DeleteMapping("/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hủy theo dõi shop", description = "Người dùng bỏ theo dõi shop.")
    @ApiResponse(responseCode = "204", description = "Đã hủy theo dõi")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void unfollow(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        shops.unfollow(userId, id);
    }
}
