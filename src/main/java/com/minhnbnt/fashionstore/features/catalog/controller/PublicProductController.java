package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Xem hàng public")
public class PublicProductController {

    private static final int MAX_BATCH_IDS = 20;

    private final CatalogService catalog;

    @GetMapping("/batch")
    @Operation(
        operationId = "batchProductSummaries", summary = "Lấy tóm tắt nhiều sản phẩm",
        description = "Chỉ product ACTIVE của shop APPROVED mới được trả về; giữ thứ tự đầu vào và bỏ qua id không hợp lệ. Tối đa 20 id."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách tóm tắt sản phẩm")
    @ApiResponse(responseCode = "400", description = "Thiếu danh sách id, vượt quá 20 id hoặc id sai định dạng UUID")
    public List<ProductSummaryResponse> batchProductSummaries(
        @Parameter(description = "Danh sách id product (tối đa 20)") @RequestParam List<UUID> ids
    ) {
        if (ids.size() > MAX_BATCH_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 20 product ids allowed");
        }
        return catalog.getSummaryBatch(ids);
    }

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
