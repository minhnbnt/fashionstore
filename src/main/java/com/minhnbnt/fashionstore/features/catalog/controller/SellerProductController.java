package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.dto.catalog.AdjustStockRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkDeleteProductsRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkImportResult;
import com.minhnbnt.fashionstore.dto.catalog.BulkOperationResult;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdatePriceRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdateProductStatusRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdateStockRequest;
import com.minhnbnt.fashionstore.dto.catalog.InventoryItemResponse;
import com.minhnbnt.fashionstore.dto.catalog.SellerProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.StockMovementResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/shops/{shopId}")
@RequiredArgsConstructor
@Tag(name = "Seller - Products & Inventory", description = "Seller quản lý danh sách sản phẩm, tồn kho và xuất nhập dữ liệu")
public class SellerProductController {

    private final CatalogService catalog;

    @GetMapping("/products")
    @Operation(summary = "Danh sách sản phẩm của shop (Seller view)", description = "Bao gồm cả hàng ACTIVE, HIDDEN, DELETED, PENDING, BANNED cùng số lượng tồn và khoảng giá.")
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<SellerProductResponse> listProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Lọc theo status (ALL, ACTIVE, HIDDEN, OUT_OF_STOCK, PENDING, BANNED, DELETED)") @RequestParam(defaultValue = "ALL") String status,
        @Parameter(description = "Lọc theo danh mục") @RequestParam(required = false) UUID categoryId,
        @Parameter(description = "Lọc mức tồn kho (ALL, LOW_STOCK, OUT_OF_STOCK)") @RequestParam(defaultValue = "ALL") String stockLevel,
        @Parameter(description = "Giá tối thiểu") @RequestParam(defaultValue = "0") long minPrice,
        @Parameter(description = "Giá tối đa") @RequestParam(defaultValue = "0") long maxPrice,
        @Parameter(description = "Tìm theo tên, slug hoặc SKU") @RequestParam(defaultValue = "") String q,
        @Parameter(description = "Sắp xếp theo: createdAt, stock, sold, price") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Thứ tự sắp xếp: asc hoặc desc") @RequestParam(defaultValue = "desc") String sortDir,
        @Parameter(description = "Trang bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số lượng mỗi trang") @RequestParam(defaultValue = "50") int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.listSellerProducts(userId, shopId, status, categoryId, stockLevel, minPrice, maxPrice, q, sortBy, sortDir, page, limit);
    }

    @PatchMapping("/products/bulk-status")
    @Operation(summary = "Thao tác hàng loạt: Ẩn / Hiện sản phẩm", description = "Cập nhật trạng thái ACTIVE hoặc HIDDEN cho danh sách sản phẩm.")
    @ApiResponse(responseCode = "200", description = "Kết quả cập nhật hàng loạt")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @SecurityRequirement(name = "bearerAuth")
    public BulkOperationResult bulkUpdateStatus(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody BulkUpdateProductStatusRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.bulkUpdateProductStatus(userId, shopId, request);
    }

    @PostMapping("/products/bulk-delete")
    @Operation(summary = "Thao tác hàng loạt: Xóa mềm sản phẩm", description = "Xóa mềm danh sách sản phẩm (bảo vệ các sản phẩm đang có đơn hàng xử lý).")
    @ApiResponse(responseCode = "200", description = "Kết quả xóa hàng loạt")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @SecurityRequirement(name = "bearerAuth")
    public BulkOperationResult bulkDeleteProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody BulkDeleteProductsRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.bulkDeleteProducts(userId, shopId, request);
    }

    @PatchMapping("/products/bulk-price")
    @Operation(summary = "Thao tác hàng loạt: Thay đổi giá sản phẩm", description = "Cập nhật giá cố định hoặc điều chỉnh theo phần trăm cho tất cả biến thể của danh sách sản phẩm.")
    @ApiResponse(responseCode = "200", description = "Kết quả đổi giá hàng loạt")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @SecurityRequirement(name = "bearerAuth")
    public BulkOperationResult bulkUpdatePrice(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody BulkUpdatePriceRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.bulkUpdatePrices(userId, shopId, request);
    }

    @PatchMapping("/products/bulk-stock")
    @Operation(summary = "Thao tác hàng loạt: Cập nhật tồn kho", description = "Gán tồn kho cố định hoặc cộng/trừ số lượng tồn cho tất cả biến thể của danh sách sản phẩm.")
    @ApiResponse(responseCode = "200", description = "Kết quả cập nhật tồn kho hàng loạt")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @SecurityRequirement(name = "bearerAuth")
    public BulkOperationResult bulkUpdateStock(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody BulkUpdateStockRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.bulkUpdateStocks(userId, shopId, request);
    }

    @GetMapping("/inventory")
    @Operation(summary = "Quản lý kho hàng", description = "Liệt kê các biến thể, có thể lọc các biến thể sắp hết hàng (lowStock=true).")
    @ApiResponse(responseCode = "200", description = "Danh sách tồn kho")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<InventoryItemResponse> inventory(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Chỉ lấy biến thể sắp hết hàng") @RequestParam(defaultValue = "false") boolean lowStock
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.listInventory(userId, shopId, lowStock);
    }

    @PatchMapping("/variants/{variantId}/stock")
    @Operation(summary = "Điều chỉnh tồn kho biến thể", description = "Cập nhật số lượng tồn kho và ghi nhật ký biến động (stock movement).")
    @ApiResponse(responseCode = "200", description = "Biến thể sau khi cập nhật kho")
    @ApiResponse(responseCode = "400", description = "Số lượng tồn không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Biến thể không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public InventoryItemResponse adjustStock(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id biến thể") @PathVariable UUID variantId,
        @Valid @RequestBody AdjustStockRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.adjustStock(userId, shopId, variantId, request);
    }

    @GetMapping("/variants/{variantId}/movements")
    @Operation(summary = "Lịch sử biến động tồn kho", description = "Xem lịch sử thay đổi tồn kho của biến thể.")
    @ApiResponse(responseCode = "200", description = "Danh sách biến động tồn kho")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Biến thể không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public List<StockMovementResponse> movements(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id biến thể") @PathVariable UUID variantId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.listStockMovements(userId, shopId, variantId);
    }

    @PostMapping(value = "/products/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập sản phẩm hàng loạt bằng file CSV", description = "File CSV định dạng: name,slug,description,sku,size,color,price,stockQuantity")
    @ApiResponse(responseCode = "200", description = "Kết quả nhập hàng loạt")
    @ApiResponse(responseCode = "400", description = "File không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc shop chưa duyệt")
    @SecurityRequirement(name = "bearerAuth")
    public BulkImportResult bulkImport(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return catalog.bulkImport(userId, shopId, file.getBytes());
    }

    @GetMapping(value = "/products/export", produces = "text/csv")
    @Operation(summary = "Xuất danh sách sản phẩm ra CSV", description = "Tải về file CSV danh sách tất cả sản phẩm và biến thể của shop.")
    @ApiResponse(responseCode = "200", description = "File CSV tải về")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<byte[]> exportProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        var csv = catalog.exportCsv(userId, shopId);
        var bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products-" + shopId + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
    }
}
