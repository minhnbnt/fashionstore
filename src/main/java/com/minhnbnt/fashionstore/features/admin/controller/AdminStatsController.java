package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.features.admin.service.AdminStatsService;

import com.minhnbnt.fashionstore.dto.admin.AdminStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin - Stats", description = "Số liệu tổng quan cho dashboard admin")
public class AdminStatsController {

    private final AdminStatsService stats;

    @GetMapping("/api/admin/stats")
    @Operation(
        operationId = "getAdminStats",
        summary = "Số liệu tổng quan",
        description = "Tài khoản theo trạng thái/role, shop theo trạng thái, đơn hàng theo trạng thái, "
            + "GMV + doanh thu COD đã thu, và xu hướng 14 ngày gần nhất (giờ Việt Nam)."
    )
    @ApiResponse(responseCode = "200", description = "Số liệu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public AdminStatsResponse get() {
        return stats.get();
    }
}
