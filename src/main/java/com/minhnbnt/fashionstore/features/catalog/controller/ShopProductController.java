package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
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
}
