package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.dto.catalog.ProductSearchRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSort;
import com.minhnbnt.fashionstore.dto.catalog.SearchFacetsResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Tìm hàng bằng BM25 + lọc theo danh mục, biến thể, giá")
public class SearchController {

    private final CatalogService catalog;

    @GetMapping("/products")
    @Operation(
        summary = "Tìm và lọc sản phẩm",
        description = "Chỉ hàng ACTIVE của shop APPROVED. Có từ khóa thì chỉ trả hàng khớp, xếp BM25; "
            + "không có từ khóa thì duyệt theo bộ lọc. Size, màu, khoảng giá phải khớp trên cùng một variant."
    )
    @ApiResponse(responseCode = "200", description = "Một trang summary kèm tổng số kết quả")
    @ApiResponse(responseCode = "400", description = "Sort lạ, giá âm, minPrice > maxPrice hoặc limit ngoài 1–100")
    public ProductSearchResponse search(
        @Parameter(description = "Từ khóa, bỏ trống để duyệt theo bộ lọc", example = "áo thun đen")
        @RequestParam(required = false)
        @Nullable String q,
        @Parameter(description = "Slug danh mục", example = "ao-khoac")
        @RequestParam(required = false)
        @Nullable String category,
        @Parameter(description = "Giá variant thấp nhất (VND)", example = "100000")
        @RequestParam(required = false)
        @Min(0)
        @Nullable Long minPrice,
        @Parameter(description = "Giá variant cao nhất (VND)", example = "500000")
        @RequestParam(required = false)
        @Min(0)
        @Nullable Long maxPrice,
        @Parameter(description = "Size variant, khớp chính xác", example = "M")
        @RequestParam(required = false)
        @Nullable String size,
        @Parameter(description = "Màu variant, khớp chính xác", example = "Đen")
        @RequestParam(required = false)
        @Nullable String color,
        @Parameter(description = "Mặc định RELEVANCE khi có từ khóa, NEWEST khi không")
        @RequestParam(required = false)
        @Nullable ProductSort sort,
        @Parameter(description = "Trang, đếm từ 0", example = "0")
        @RequestParam(defaultValue = "0")
        @Min(0)
        @Max(10000)
        int page,
        @Parameter(description = "Số kết quả mỗi trang", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100)
        int limit
    ) {
        var request = new ProductSearchRequest(q, category, minPrice, maxPrice, size, color, sort, page, limit);
        return catalog.search(request);
    }

    @GetMapping("/facets")
    @Operation(
        summary = "Lựa chọn cho bộ lọc",
        description = "Size, màu và khoảng giá có trong tập khớp từ khóa + danh mục, để dựng bộ lọc."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách size, màu và giá min/max")
    public SearchFacetsResponse facets(
        @Parameter(description = "Từ khóa, bỏ trống = mọi sản phẩm", example = "áo")
        @RequestParam(required = false)
        @Nullable String q,
        @Parameter(description = "Slug danh mục", example = "ao-khoac")
        @RequestParam(required = false)
        @Nullable String category
    ) {
        return catalog.facets(q, category);
    }
}
