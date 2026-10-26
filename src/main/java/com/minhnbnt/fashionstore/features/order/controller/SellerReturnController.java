package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/returns")
@RequiredArgsConstructor
@Tag(name = "Seller - Returns", description = "Seller xử lý yêu cầu đổi trả của khách")
public class SellerReturnController {

    private final ReturnRequestService returns;

    @GetMapping
    @Operation(
        summary = "Danh sách yêu cầu đổi/trả",
        description = "Cần quyền RETURN_PROCESS. Lọc theo shop hoặc trạng thái "
            + "(REQUESTED, APPROVED, REJECTED, COMPLETED)."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách yêu cầu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<ReturnRequestResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam(required = false) UUID shopId,
        @Parameter(description = "Trạng thái lọc") @RequestParam(required = false) String status
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return returns.listSellerReturns(userId, shopId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết yêu cầu đổi/trả")
    @ApiResponse(responseCode = "200", description = "Chi tiết yêu cầu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền RETURN_PROCESS")
    @ApiResponse(responseCode = "404", description = "Yêu cầu không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ReturnRequestResponse detail(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id yêu cầu") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return returns.getDetail(userId, id);
    }

    @PatchMapping("/{id}/approve")
    @Operation(
        summary = "Chấp thuận yêu cầu đổi/trả",
        description = "Seller đồng ý và chọn phương án hoàn tiền/đổi hàng."
    )
    @ApiResponse(responseCode = "200", description = "Yêu cầu sau khi duyệt")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền RETURN_PROCESS")
    @ApiResponse(responseCode = "404", description = "Yêu cầu không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public ReturnRequestResponse approve(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id yêu cầu") @PathVariable UUID id,
        @Valid @RequestBody ApproveReturnRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return returns.approve(userId, id, request);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu đổi/trả", description = "Seller từ chối kèm lý do.")
    @ApiResponse(responseCode = "200", description = "Yêu cầu sau khi từ chối")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền RETURN_PROCESS")
    @ApiResponse(responseCode = "404", description = "Yêu cầu không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public ReturnRequestResponse reject(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id yêu cầu") @PathVariable UUID id,
        @Valid @RequestBody RejectReturnRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return returns.reject(userId, id, request);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Xác nhận hoàn tất đổi/trả", description = "APPROVED -> COMPLETED.")
    @ApiResponse(responseCode = "200", description = "Yêu cầu sau khi hoàn tất")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền RETURN_PROCESS")
    @ApiResponse(responseCode = "404", description = "Yêu cầu không tồn tại")
    @ApiResponse(responseCode = "409", description = "Sai trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    public ReturnRequestResponse complete(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id yêu cầu") @PathVariable UUID id,
        @RequestParam(required = false) String note
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return returns.complete(userId, id, note);
    }
}
