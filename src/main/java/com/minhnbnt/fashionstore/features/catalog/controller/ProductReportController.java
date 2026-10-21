package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.dto.catalog.CreateProductReportRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductReportResponse;
import com.minhnbnt.fashionstore.features.catalog.ProductReportService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Product Reports", description = "Người mua báo cáo sản phẩm nghi vi phạm")
public class ProductReportController {

    private final ProductReportService reports;

    @PostMapping("/api/products/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "reportProduct",
        summary = "Báo cáo sản phẩm",
        description = "Đưa sản phẩm vào hàng đợi kiểm duyệt của admin. Mỗi người 1 báo cáo đang mở cho mỗi sản phẩm; "
            + "thành viên shop không tự báo cáo hàng của shop mình."
    )
    @ApiResponse(responseCode = "201", description = "Đã ghi nhận báo cáo")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc mô tả quá dài")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "404", description = "Sản phẩm không tồn tại hoặc không còn hiển thị")
    @ApiResponse(responseCode = "409", description = "Đã có báo cáo đang chờ xử lý, hoặc là sản phẩm của shop mình")
    @SecurityRequirement(name = "bearerAuth")
    public ProductReportResponse report(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id sản phẩm") @PathVariable UUID id,
        @Valid @RequestBody CreateProductReportRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return reports.report(UUID.fromString(subject), id, request);
    }
}
