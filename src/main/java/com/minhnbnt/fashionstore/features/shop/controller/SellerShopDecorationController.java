package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.DecorationResponse;
import com.minhnbnt.fashionstore.dto.shop.SaveDraftLayoutRequest;
import com.minhnbnt.fashionstore.features.shop.ShopDecorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/shops/{shopId}/decoration")
@RequiredArgsConstructor
@Tag(name = "Seller - Decoration", description = "Trình thiết kế và trang trí giao diện gian hàng (kéo-thả)")
public class SellerShopDecorationController {

    private final ShopDecorationService decorations;

    @GetMapping
    @Operation(
        operationId = "getDecoration",
        summary = "Xem giao diện và bản nháp hiện tại",
        description = "Lấy cả layout đang hiển thị và bản nháp (draft) đang chỉnh sửa."
    )
    @ApiResponse(responseCode = "200", description = "Thông tin trang trí shop")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public DecorationResponse getDecoration(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return decorations.getSellerDecoration(userId, shopId);
    }

    @PutMapping("/draft")
    @Operation(
        operationId = "saveDraft",
        summary = "Lưu nháp bố cục trang trí",
        description = "Lưu lại bản nháp các block (Banner, Sản phẩm nổi bật, Bộ sưu tập, Đếm ngược Flash Sale, Video) để xem trước."
    )
    @ApiResponse(responseCode = "200", description = "Bản nháp sau khi lưu")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền DECORATION_WRITE")
    @SecurityRequirement(name = "bearerAuth")
    public DecorationResponse saveDraft(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody SaveDraftLayoutRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return decorations.saveDraft(userId, shopId, request.layoutJson());
    }

    @PostMapping("/publish")
    @Operation(
        operationId = "publish",
        summary = "Xuất bản bản nháp lên trang chủ shop",
        description = "Chuyển draft thành layout chính thức hiển thị cho khách hàng."
    )
    @ApiResponse(responseCode = "200", description = "Layout sau khi xuất bản")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền DECORATION_WRITE")
    @SecurityRequirement(name = "bearerAuth")
    public DecorationResponse publish(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return decorations.publish(userId, shopId);
    }

    @DeleteMapping("/draft")
    @Operation(
        operationId = "discardDraft",
        summary = "Hủy bỏ bản nháp",
        description = "Hủy các thay đổi trong bản nháp chưa xuất bản."
    )
    @ApiResponse(responseCode = "200", description = "Layout sau khi hủy nháp")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền DECORATION_WRITE")
    @SecurityRequirement(name = "bearerAuth")
    public DecorationResponse discardDraft(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return decorations.discardDraft(userId, shopId);
    }
}
