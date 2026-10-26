package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.order.DailyRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.RevenueSummaryResponse;
import com.minhnbnt.fashionstore.dto.order.SettlementResponse;
import com.minhnbnt.fashionstore.dto.order.TopProductRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.WithdrawalResponse;
import com.minhnbnt.fashionstore.features.order.RevenueService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@Tag(name = "Seller - Revenue & Finance", description = "Quản lý doanh thu, giải ngân và rút tiền")
public class SellerRevenueController {

    private final RevenueService revenue;

    @GetMapping("/revenue/summary")
    @Operation(
        summary = "Tổng quan doanh thu",
        description = "Tổng doanh thu đơn đã giao, số lượng đơn, giá trị trung bình đơn, "
                + "số dư ví khả dụng và đang giữ."
    )
    @ApiResponse(responseCode = "200", description = "Dữ liệu tổng quan doanh thu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVENUE_READ")
    @SecurityRequirement(name = "bearerAuth")
    public RevenueSummaryResponse summary(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.getSummary(userId, shopId);
    }

    @GetMapping("/revenue/by-day")
    @Operation(summary = "Biểu đồ doanh thu theo ngày", description = "Doanh thu từng ngày của shop để vẽ biểu đồ.")
    @ApiResponse(responseCode = "200", description = "Doanh thu theo ngày")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVENUE_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<DailyRevenueResponse> byDay(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.getDailyRevenue(userId, shopId);
    }

    @GetMapping("/revenue/top-products")
    @Operation(
        summary = "Top sản phẩm bán chạy",
        description = "Danh sách sản phẩm có doanh thu và số lượng bán cao nhất."
    )
    @ApiResponse(responseCode = "200", description = "Top sản phẩm")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVENUE_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<TopProductRevenueResponse> topProducts(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Số lượng giới hạn") @RequestParam(defaultValue = "10") int limit
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.getTopProducts(userId, shopId, limit);
    }

    @GetMapping("/settlements")
    @Operation(
        summary = "Danh sách giải ngân (Settlements)",
        description = "Lọc các khoản tiền đang giữ (HOLDING) hoặc đã vào ví (RELEASED)."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách giải ngân")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVENUE_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<SettlementResponse> settlements(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Lọc trạng thái (HOLDING, RELEASED, WITHDRAWN)")
        @RequestParam(required = false) String status
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.listSettlements(userId, shopId, status);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Yêu cầu rút tiền về ngân hàng", description = "Cần quyền REVENUE_WITHDRAW.")
    @ApiResponse(responseCode = "201", description = "Yêu cầu rút tiền đã tạo")
    @ApiResponse(responseCode = "400", description = "Số dư không đủ hoặc thông tin ngân hàng thiếu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Thiếu quyền REVENUE_WITHDRAW")
    @SecurityRequirement(name = "bearerAuth")
    public WithdrawalResponse requestWithdrawal(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.createWithdrawal(userId, request);
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Lịch sử rút tiền", description = "Danh sách các yêu cầu rút tiền của shop.")
    @ApiResponse(responseCode = "200", description = "Danh sách yêu cầu rút tiền")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403",
            description = "Không phải thành viên shop hoặc thiếu quyền REVENUE_READ")
    @SecurityRequirement(name = "bearerAuth")
    public List<WithdrawalResponse> listWithdrawals(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return revenue.listWithdrawals(userId, shopId);
    }
}
