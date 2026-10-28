package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductCollectionType;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopProductSort;
import com.minhnbnt.fashionstore.features.shop.ShopCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}")
@RequiredArgsConstructor
@Tag(
    name = "Shops - Catalog",
    description = "Xem sản phẩm và danh mục công khai của shop (kèm tìm kiếm nội bộ)"
)
public class ShopCatalogController {

    private final ShopCatalogService shopCatalog;

    @GetMapping("/products")
    @Operation(
        operationId = "getShopProducts",
        summary = "Danh sách sản phẩm của shop",
        description = """
            Chỉ trả về sản phẩm ACTIVE của shop APPROVED.
            Hỗ trợ tìm kiếm từ khóa trong shop, lọc bộ sưu tập nổi bật và danh mục.
            """
    )
    @ApiResponse(responseCode = "200", description = "Một trang sản phẩm của shop kèm tổng số kết quả")
    @ApiResponse(responseCode = "400", description = "Tham số sort hoặc collection không hợp lệ")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại hoặc chưa được duyệt")
    public PageResponse<ProductResponse> getShopProducts(
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Từ khóa tìm kiếm trong shop", example = "áo")
        @RequestParam(required = false)
        @Nullable String q,
        @Parameter(description = "Loại bộ sưu tập nổi bật: NEW_ARRIVALS, BEST_SELLER, SALE_OFF")
        @RequestParam(required = false)
        @Nullable ShopProductCollectionType collection,
        @Parameter(description = "Id danh mục cần lọc")
        @RequestParam(required = false)
        @Nullable UUID categoryId,
        @Parameter(description = "Kiểu sắp xếp: NEWEST, BEST_SELLER, PRICE_ASC, PRICE_DESC")
        @RequestParam(required = false)
        @Nullable ShopProductSort sort,
        @Parameter(description = "Số trang, đếm từ 0", example = "0")
        @RequestParam(defaultValue = "0")
        int page,
        @Parameter(description = "Số lượng sản phẩm mỗi trang", example = "12")
        @RequestParam(defaultValue = "12")
        int limit
    ) {
        var request = new ShopProductQueryRequest(q, collection, categoryId, sort, page, limit);
        return shopCatalog.getShopProducts(shopId, request);
    }

    @GetMapping("/categories")
    @Operation(
        operationId = "getShopCategories",
        summary = "Danh mục sản phẩm của shop",
        description = "Lấy danh sách các danh mục mà shop thực tế đang có sản phẩm kinh doanh ACTIVE."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách danh mục kèm số lượng sản phẩm")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại hoặc chưa được duyệt")
    public List<ShopCategorySummaryResponse> getShopCategories(
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        return shopCatalog.getShopCategories(shopId);
    }
}
