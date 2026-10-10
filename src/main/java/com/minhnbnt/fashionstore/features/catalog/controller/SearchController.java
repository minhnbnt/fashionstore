package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Search", description = "Tìm hàng bằng BM25")
public class SearchController {

    private final CatalogService catalog;

    @GetMapping
    @Operation(
        summary = "Tìm sản phẩm",
        description = "Xếp hạng BM25, chỉ hàng ACTIVE của shop APPROVED."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách summary nhẹ kèm điểm rank")
    @ApiResponse(responseCode = "400", description = "Thiếu từ khóa")
    public List<ProductSummaryResponse> search(
        @Parameter(description = "Từ khóa", example = "áo thun đen")
        @RequestParam String q,
        @Parameter(description = "Số kết quả tối đa", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100)
        int limit
    ) {
        return catalog.search(q, limit);
    }
}
