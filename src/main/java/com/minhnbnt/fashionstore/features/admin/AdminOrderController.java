package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.AdminCancelOrderRequest;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.model.OrderAlert;
import com.minhnbnt.fashionstore.model.OrderStatus;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin - Orders", description = "Giám sát đơn hàng toàn sàn, cảnh báo SLA, hủy can thiệp. Cần role admin.")
public class AdminOrderController {

    private final AdminOrderService adminOrders;

    @GetMapping
    @Operation(
        operationId = "listAdminOrders", summary = "Danh sách đơn toàn sàn",
        description = "Lọc theo trạng thái, cảnh báo SLA, shop, người mua, khoảng ngày đặt (giờ VN) + tìm theo "
            + "mã đơn, tên/SĐT người nhận, tên shop, email người mua. Mặc định mới đặt trước; lọc theo cảnh báo "
            + "thì đơn trễ lâu nhất lên đầu."
    )
    @ApiResponse(responseCode = "200", description = "Trang kết quả")
    @ApiResponse(responseCode = "400", description = "Tham số lọc, khoảng ngày hoặc phân trang sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<AdminOrderResponse> list(
        @Parameter(description = "Trạng thái, bỏ trống = tất cả", example = "PENDING")
        @RequestParam(required = false) @Nullable OrderStatus status,
        @Parameter(description = "Cảnh báo SLA, bỏ trống = không lọc", example = "CONFIRM_LATE")
        @RequestParam(required = false) @Nullable OrderAlert alert,
        @Parameter(description = "Chỉ lấy đơn của shop này")
        @RequestParam(required = false) @Nullable UUID shopId,
        @Parameter(description = "Chỉ lấy đơn của người mua này")
        @RequestParam(required = false) @Nullable UUID buyerId,
        @Parameter(description = "Từ khóa: mã đơn, tên/SĐT người nhận, tên shop, email người mua")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Đặt từ ngày (giờ VN), yyyy-MM-dd", example = "2026-10-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
        @Parameter(description = "Đặt đến hết ngày (giờ VN), yyyy-MM-dd", example = "2026-10-18")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
        @Parameter(description = "Trang, đếm từ 0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Số đơn mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var filter = new AdminOrderFilter(status, alert, shopId, buyerId, q, from, to);
        return adminOrders.search(filter, page, size);
    }

    @GetMapping("/summary")
    @Operation(
        operationId = "getAdminOrderSummary", summary = "Đếm đơn theo trạng thái và cảnh báo",
        description = "Cùng bộ lọc với danh sách nhưng không lọc trạng thái/cảnh báo, để hiện số trên từng tab và "
            + "thẻ cảnh báo: chờ xác nhận quá 24 giờ, chưa bàn giao vận chuyển sau 48 giờ, đang giao quá 7 ngày, "
            + "trả hàng chưa xong."
    )
    @ApiResponse(responseCode = "200", description = "Số đếm")
    @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc khoảng ngày sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public AdminOrderSummaryResponse summary(
        @Parameter(description = "Chỉ đếm đơn của shop này")
        @RequestParam(required = false) @Nullable UUID shopId,
        @Parameter(description = "Chỉ đếm đơn của người mua này")
        @RequestParam(required = false) @Nullable UUID buyerId,
        @Parameter(description = "Từ khóa: mã đơn, tên/SĐT người nhận, tên shop, email người mua")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Đặt từ ngày (giờ VN), yyyy-MM-dd", example = "2026-10-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
        @Parameter(description = "Đặt đến hết ngày (giờ VN), yyyy-MM-dd", example = "2026-10-18")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to
    ) {
        var filter = new AdminOrderFilter(null, null, shopId, buyerId, q, from, to);
        return adminOrders.summary(filter);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getAdminOrder",
        summary = "Chi tiết đơn",
        description = "Đơn + shop + người mua + sản phẩm + tiền + dòng thời gian (ai làm gì, lúc nào) "
            + "+ yêu cầu trả hàng."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết đơn")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public AdminOrderDetailResponse detail(@Parameter(description = "id đơn") @PathVariable UUID id) {
        return adminOrders.getDetail(id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(
        operationId = "adminCancelOrder", summary = "Hủy can thiệp đơn",
        description = "PENDING/CONFIRMED → CANCELLED kèm loại lý do + ghi chú, ghi nhận admin là người hủy. "
            + "Đơn đã xác nhận được hoàn kho. Đơn đã bàn giao vận chuyển không hủy được."
    )
    @ApiResponse(responseCode = "200", description = "Đơn đã hủy")
    @ApiResponse(responseCode = "400", description = "Thiếu loại lý do hoặc ghi chú không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    @ApiResponse(responseCode = "409", description = "Đơn đã bàn giao vận chuyển, đã giao hoặc đã hủy")
    @SecurityRequirement(name = "bearerAuth")
    public AdminOrderResponse cancel(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id đơn") @PathVariable UUID id,
        @Valid @RequestBody AdminCancelOrderRequest request
    ) {
        return adminOrders.cancel(adminIdOf(jwt), id, request.reason(), request.note());
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
