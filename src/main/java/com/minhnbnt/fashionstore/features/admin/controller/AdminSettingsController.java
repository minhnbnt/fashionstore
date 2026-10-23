package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.settings.ConfigAuditResponse;
import com.minhnbnt.fashionstore.dto.settings.SystemSettingsResponse;
import com.minhnbnt.fashionstore.dto.settings.UpdateSettingsRequest;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.settings.SystemSettingsService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Settings", description = "Cấu hình chung của sàn và lịch sử thay đổi cấu hình. Cần role admin.")
public class AdminSettingsController {

    private final SystemSettingsService settings;

    private final ConfigAuditService audits;

    @GetMapping("/settings")
    @Operation(
        operationId = "getSystemSettings",
        summary = "Xem cấu hình chung",
        description = "Toàn bộ cấu hình chung hiện hành: thông tin sàn, vận chuyển, giữ đơn, tự hoàn tất, giới hạn COD."
    )
    @ApiResponse(responseCode = "200", description = "Cấu hình hiện hành")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public SystemSettingsResponse get() {
        return SystemSettingsService.toResponse(settings.current());
    }

    @PutMapping("/settings")
    @Operation(
        operationId = "updateSystemSettings",
        summary = "Cập nhật cấu hình chung",
        description = "Cập nhật một phần: field bỏ trống giữ nguyên. Chỉ key đổi giá trị mới được ghi lịch sử. "
            + "codMaxTotal = 0 nghĩa là không giới hạn, ngược lại phải không nhỏ hơn codMinTotal."
    )
    @ApiResponse(responseCode = "200", description = "Cấu hình sau cập nhật")
    @ApiResponse(responseCode = "400", description = "Giá trị ngoài khoảng cho phép hoặc COD tối đa nhỏ hơn tối thiểu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public SystemSettingsResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UpdateSettingsRequest request
    ) {
        return settings.update(adminIdOf(jwt), request);
    }

    @GetMapping("/config-audit")
    @Operation(
        operationId = "listConfigAudit",
        summary = "Lịch sử thay đổi cấu hình",
        description = "Các thay đổi cấu hình của admin, mới nhất lên đầu; lọc theo loại đối tượng."
    )
    @ApiResponse(responseCode = "200", description = "Trang lịch sử")
    @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc phân trang sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<ConfigAuditResponse> listAudit(
        @Parameter(description = "Loại đối tượng, bỏ trống = tất cả", example = "SETTING")
        @RequestParam(required = false) @Nullable ConfigAuditEntity entity,
        @Parameter(description = "Trang, đếm từ 0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Số dòng mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return audits.page(entity, page, size);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
