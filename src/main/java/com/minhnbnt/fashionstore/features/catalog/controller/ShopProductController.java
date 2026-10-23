package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductAuditLogResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
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
@RequestMapping("/api/shops/{shopId}/products")
@RequiredArgsConstructor
@Tag(name = "Shop - Products", description = "Seller quản lý hàng của shop mình")
public class ShopProductController {

    private final CatalogService catalog;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo product kèm variants",
        description = "OWNER/STAFF, shop phải APPROVED. Product tạo phát ACTIVE luôn, đúng 1 variant default."
    )
    @ApiResponse(responseCode = "201", description = "Product vừa tạo")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Shop hoặc category không tồn tại")
    @ApiResponse(responseCode = "409", description = "Slug hoặc SKU đã có người dùng")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody CreateProductRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.createProduct(userId, shopId, request);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Chi tiết product cho seller",
        description = "Lấy chi tiết product thuộc shop để edit hoặc xem."
    )
    @ApiResponse(responseCode = "200", description = "Product detail")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Product không thuộc shop này")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse getDetail(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.getSellerProductDetail(userId, shopId, id);
    }

    @GetMapping("/{id}/audit-logs")
    @Operation(
        summary = "Lịch sử chỉnh sửa sản phẩm",
        description = "Lấy danh sách audit log theo thời gian giảm dần."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách audit log")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Product không thuộc shop này")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProductAuditLogResponse> getAuditLogs(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.getAuditLogs(userId, shopId, id);
    }

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "Ẩn/hiện sản phẩm (toggle status)",
        description = "Chuyển trạng thái giữa ACTIVE và HIDDEN."
    )
    @ApiResponse(responseCode = "200", description = "Product sau khi đổi status")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Product không thuộc shop này")
    @ApiResponse(responseCode = "409", description = "Trạng thái không thể toggle")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse toggleStatus(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.toggleProductStatus(userId, shopId, id);
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Sửa product",
        description = "Id product phải thuộc đúng shop trên path, không là 404. Field null = giữ nguyên."
    )
    @ApiResponse(responseCode = "200", description = "Product sau update")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Product không thuộc shop này")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.updateProduct(userId, shopId, id, request);
    }

    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Gắn ảnh vào product",
        description = "Body là list S3 key đã upload qua presign. DB chỉ lưu key."
    )
    @ApiResponse(responseCode = "201", description = "Danh sách ảnh vừa gắn")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @ApiResponse(responseCode = "404", description = "Product không thuộc shop này")
    @SecurityRequirement(name = "bearerAuth")
    public List<ImageResponse> attachImages(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id,
        @Valid @RequestBody AttachImagesRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.attachImages(userId, shopId, id, request);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm sản phẩm", description = "Chuyển trạng thái sang DELETED. Không thể xóa nếu có đơn đang xử lý.")
    @ApiResponse(responseCode = "204", description = "Đã xóa sản phẩm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Product không tồn tại")
    @ApiResponse(responseCode = "409", description = "Còn đơn hàng đang xử lý")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteProduct(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        catalog.deleteProduct(userId, shopId, id);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa ảnh sản phẩm", description = "Gỡ ảnh khỏi sản phẩm.")
    @ApiResponse(responseCode = "204", description = "Đã xóa ảnh")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Ảnh hoặc product không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteImage(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id product") @PathVariable UUID id,
        @Parameter(description = "Id image") @PathVariable UUID imageId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        catalog.deleteProductImage(userId, shopId, id, imageId);
    }
}
