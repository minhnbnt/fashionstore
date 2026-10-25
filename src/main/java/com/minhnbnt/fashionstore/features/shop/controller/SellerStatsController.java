package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.AlertResponse;
import com.minhnbnt.fashionstore.dto.shop.CustomerStatsResponse;
import com.minhnbnt.fashionstore.dto.shop.FollowerTrendResponse;
import com.minhnbnt.fashionstore.dto.shop.OrderStatusSummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.PeriodComparisonResponse;
import com.minhnbnt.fashionstore.dto.shop.ProductStatsResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueByCategoryResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueByPaymentResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopDashboardResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopPerformanceResponse;
import com.minhnbnt.fashionstore.dto.shop.TopCustomerResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/shops/{shopId}/stats")
@RequiredArgsConstructor
@Validated
@Tag(name = "Seller - Statistics", description = "Thống kê và Báo cáo dành cho Shop")
public class SellerStatsController {

    private final ShopStatsService stats;

    @GetMapping("/dashboard")
    @Operation(
        operationId = "getShopDashboard",
        summary = "Dashboard tổng quan shop",
        description = "Doanh thu, đơn hàng, lượt truy cập hôm nay và so sánh với hôm qua."
    )
    @ApiResponse(responseCode = "200", description = "Dữ liệu dashboard tổng quan")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public ShopDashboardResponse dashboard(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getDashboard(userId, shopId);
    }

    @GetMapping("/performance")
    @Operation(
        operationId = "getShopPerformance",
        summary = "Hiệu suất hoạt động của shop",
        description = "Tỷ lệ phản hồi chat, tỷ lệ hủy đơn, tỷ lệ giao đúng hạn và đánh giá trung bình."
    )
    @ApiResponse(responseCode = "200", description = "Chỉ số hiệu suất của shop")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public ShopPerformanceResponse performance(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getPerformance(userId, shopId);
    }

    @GetMapping("/alerts")
    @Operation(
        operationId = "getShopAlerts",
        summary = "Cảnh báo vận hành",
        description = "Đơn sắp quá hạn, sản phẩm hết hàng hoặc khiếu nại chưa xử lý."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách cảnh báo")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<AlertResponse> alerts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getAlerts(userId, shopId);
    }

    @GetMapping("/revenue/detail")
    @Operation(
        operationId = "getRevenueDetail",
        summary = "Biểu đồ doanh thu chi tiết",
        description = "Doanh thu theo chu kỳ ngày, tuần, tháng hoặc năm trong khoảng thời gian chỉ định."
    )
    @ApiResponse(responseCode = "200", description = "Dữ liệu doanh thu theo chu kỳ")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public RevenueDetailResponse revenueDetail(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Chu kỳ (day, week, month, year)")
        @RequestParam(defaultValue = "day")
        @Pattern(regexp = "(?i)day|week|month|year", message = "Period must be day, week, month, or year")
        String period,
        @Parameter(description = "Từ ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate from,
        @Parameter(description = "Đến ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate to
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getRevenueDetail(userId, shopId, period, from, to);
    }

    @GetMapping("/revenue/by-category")
    @Operation(
        operationId = "getRevenueByCategory",
        summary = "Doanh thu theo danh mục",
        description = "Doanh thu và số lượng bán phân bổ theo từng danh mục sản phẩm."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách doanh thu theo danh mục")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<RevenueByCategoryResponse> revenueByCategory(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getRevenueByCategory(userId, shopId);
    }

    @GetMapping("/revenue/by-payment")
    @Operation(
        operationId = "getRevenueByPayment",
        summary = "Doanh thu theo hình thức thanh toán",
        description = "Doanh thu và số lượng đơn hàng theo phương thức thanh toán (COD, v.v.)."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách doanh thu theo phương thức thanh toán")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<RevenueByPaymentResponse> revenueByPayment(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getRevenueByPayment(userId, shopId);
    }

    @GetMapping("/orders/summary")
    @Operation(
        operationId = "getOrderStatusSummary",
        summary = "Báo cáo tổng kết trạng thái đơn hàng",
        description = "Số lượng và tổng giá trị của đơn hoàn thành, đơn hủy và đơn đổi trả."
    )
    @ApiResponse(responseCode = "200", description = "Tổng kết trạng thái đơn hàng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public OrderStatusSummaryResponse orderStatusSummary(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Từ ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate from,
        @Parameter(description = "Đến ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate to
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getOrderStatusSummary(userId, shopId, from, to);
    }

    @GetMapping("/revenue/comparison")
    @Operation(
        operationId = "getPeriodComparison",
        summary = "So sánh hiệu suất doanh thu",
        description = "So sánh doanh thu và số đơn giữa chu kỳ hiện tại và chu kỳ liền kề trước đó."
    )
    @ApiResponse(responseCode = "200", description = "Số liệu so sánh chu kỳ")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public PeriodComparisonResponse periodComparison(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Chu kỳ so sánh (day, week, month, year)")
        @RequestParam(defaultValue = "day")
        @Pattern(regexp = "(?i)day|week|month|year", message = "Period must be day, week, month, or year")
        String period
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getPeriodComparison(userId, shopId, period);
    }

    @GetMapping("/products/top-selling")
    @Operation(
        operationId = "getTopSellingProducts",
        summary = "Top sản phẩm bán chạy",
        description = "Danh sách sản phẩm có doanh thu và số lượng bán cao nhất."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm bán chạy")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProductStatsResponse> topSellingProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Số lượng giới hạn (1-100)")
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 100, message = "Limit must not exceed 100")
        int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getTopSellingProducts(userId, shopId, limit);
    }

    @GetMapping("/products/low-conversion")
    @Operation(
        operationId = "getLowConversionProducts",
        summary = "Sản phẩm có tỷ lệ chuyển đổi thấp",
        description = "Sản phẩm có lượt xem cao nhưng lượt mua thấp cần tối ưu nội dung/giá bán."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm chuyển đổi thấp")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProductStatsResponse> lowConversionProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Số lượng giới hạn (1-100)")
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 100, message = "Limit must not exceed 100")
        int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getLowConversionProducts(userId, shopId, limit);
    }

    @GetMapping("/products/out-of-stock")
    @Operation(
        operationId = "getOutOfStockProducts",
        summary = "Sản phẩm hết hàng",
        description = "Danh sách sản phẩm có tồn kho bằng 0 cần nhập thêm hàng."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm hết hàng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProductStatsResponse> outOfStockProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getOutOfStockProducts(userId, shopId);
    }

    @GetMapping("/products/slow-moving")
    @Operation(
        operationId = "getSlowMovingProducts",
        summary = "Sản phẩm tồn kho lâu ngày",
        description = "Sản phẩm còn tồn kho nhưng không phát sinh đơn hàng trong hơn 30 ngày."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách sản phẩm tồn kho lâu")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProductStatsResponse> slowMovingProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Số lượng giới hạn (1-100)")
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 100, message = "Limit must not exceed 100")
        int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getSlowMovingProducts(userId, shopId, limit);
    }

    @GetMapping("/customers/overview")
    @Operation(
        operationId = "getCustomerOverview",
        summary = "Tổng quan hành vi khách hàng",
        description = "Tỷ lệ khách mới vs quay lại, tỷ lệ giữ chân (retention) và giá trị đơn trung bình (AOV)."
    )
    @ApiResponse(responseCode = "200", description = "Tổng quan khách hàng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public CustomerStatsResponse customerOverview(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getCustomerOverview(userId, shopId);
    }

    @GetMapping("/customers/top")
    @Operation(
        operationId = "getTopCustomers",
        summary = "Top khách hàng mua nhiều nhất",
        description = "Danh sách khách hàng có tổng chi tiêu và số lượng đơn cao nhất."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách top khách hàng")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<TopCustomerResponse> topCustomers(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Số lượng giới hạn (1-100)")
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 100, message = "Limit must not exceed 100")
        int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getTopCustomers(userId, shopId, limit);
    }

    @GetMapping("/customers/followers")
    @Operation(
        operationId = "getFollowerTrend",
        summary = "Xu hướng người theo dõi shop",
        description = "Tổng số người theo dõi và biến động tăng/giảm so với tháng trước."
    )
    @ApiResponse(responseCode = "200", description = "Số liệu người theo dõi")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public FollowerTrendResponse followerTrend(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        return stats.getFollowerTrend(userId, shopId);
    }

    private static final java.time.ZoneId VN_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    @GetMapping("/revenue/export")
    @Operation(
        operationId = "exportRevenueReport",
        summary = "Xuất file báo cáo doanh thu",
        description = "Tải về báo cáo doanh thu dưới định dạng Excel (.xlsx) đa sheet hoặc CSV theo khoảng thời gian."
    )
    @ApiResponse(responseCode = "200", description = "File báo cáo doanh thu")
    @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy gian hàng")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<byte[]> exportRevenue(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID shop") @PathVariable UUID shopId,
        @Parameter(description = "Định dạng file (xlsx, csv)")
        @RequestParam(defaultValue = "xlsx")
        @Pattern(regexp = "(?i)csv|xlsx", message = "Format must be csv or xlsx")
        String format,
        @Parameter(description = "Từ ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate from,
        @Parameter(description = "Đến ngày (YYYY-MM-DD)") @RequestParam(required = false) LocalDate to
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);
        var bytes = stats.exportRevenueReport(userId, shopId, format, from, to);

        var extension = ".xlsx";
        var mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if ("csv".equalsIgnoreCase(format)) {
            extension = ".csv";
            mediaType = MediaType.parseMediaType("text/csv; charset=UTF-8");
        }
        var filename = "bao-cao-doanh-thu-" + shopId + "-" + LocalDate.now(VN_ZONE) + extension;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(mediaType)
            .body(bytes);
    }
}
