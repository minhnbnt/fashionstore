package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.model.ShopStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shops")
@RequiredArgsConstructor
@Tag(name = "Admin - Shops", description = "Duyệt shop, cần role admin trong GoTrue app_metadata")
public class AdminShopController {

    private final ShopService shops;

    @GetMapping
    @Operation(summary = "Liệt kê shop theo trạng thái", description = "Mặc định PENDING để admin biết lối mà duyệt.")
    @ApiResponse(responseCode = "200", description = "Danh sách shop mới nhất trước")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<ShopResponse> list(
        @Parameter(description = "Trạng thái cần lọc", example = "PENDING")
        @RequestParam(defaultValue = "PENDING") ShopStatus status
    ) {
        return shops.listByStatus(status);
    }

    @PatchMapping("/{id}/approve")
    @Operation(
        summary = "Duyệt shop",
        description = "PENDING → APPROVED. Role admin đọc từ claim app_metadata của JWT."
    )
    @ApiResponse(responseCode = "200", description = "Shop đã duyệt")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái PENDING")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse approve(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var adminId = UUID.fromString(subject);

        return shops.approve(adminId, id);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Từ chối shop", description = "PENDING → REJECTED.")
    @ApiResponse(responseCode = "200", description = "Shop đã từ chối")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái PENDING")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse reject(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var adminId = UUID.fromString(subject);

        return shops.reject(adminId, id);
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Treo shop vi phạm", description = "APPROVED → SUSPENDED. Sản phẩm của shop tự ẩn.")
    @ApiResponse(responseCode = "200", description = "Shop đã bị treo")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái APPROVED")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse suspend(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var adminId = UUID.fromString(subject);

        return shops.suspend(adminId, id);
    }

    @PatchMapping("/{id}/unsuspend")
    @Operation(summary = "Gỡ treo shop", description = "SUSPENDED → APPROVED.")
    @ApiResponse(responseCode = "200", description = "Shop đã mở lại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @ApiResponse(responseCode = "409", description = "Shop không ở trạng thái SUSPENDED")
    @SecurityRequirement(name = "bearerAuth")
    public ShopResponse unsuspend(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id shop") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var adminId = UUID.fromString(subject);

        return shops.unsuspend(adminId, id);
    }
}
