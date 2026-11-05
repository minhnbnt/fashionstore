package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @GetMapping("/batch")
    @Operation(
        operationId = "batchProductSummaries", summary = "Lấy summary nhiều sản phẩm theo id",
        description = "Chỉ product ACTIVE của shop APPROVED, đúng thứ tự id truyền vào; "
            + "id không tìm thấy hoặc không hiển thị được bị bỏ qua."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách summary theo thứ tự id")
    @ApiResponse(responseCode = "400", description = "Quá 20 id hoặc id sai định dạng")
    public List<ProductSummaryResponse> batchSummaries(
        @Parameter(description = "Id product, phân cách bởi dấu phẩy")
        @RequestParam
        @Size(max = 20)
        List<UUID> ids
    ) {
        return catalog.getSummaries(ids);
    }
}
