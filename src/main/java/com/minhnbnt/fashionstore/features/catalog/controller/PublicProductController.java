package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Xem hàng public")
public class PublicProductController {

    private final CatalogService catalog;

    @GetMapping("/{id}")
    @Operation(
        operationId = "detail", summary = "Chi tiết sản phẩm",
        description = "Chỉ product ACTIVE của shop APPROVED mới xem được, còn lại 404."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết kèm variants và ảnh")
    @ApiResponse(responseCode = "404", description = "Không có, bị ẩn hoặc shop chưa duyệt")
    public ProductResponse detail(
        @Parameter(description = "Id product") @PathVariable UUID id
    ) {
        return catalog.getDetail(id);
    }
}
