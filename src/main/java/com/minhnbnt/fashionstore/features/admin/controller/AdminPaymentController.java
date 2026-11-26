package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.payment.AdminWithdrawalResponse;
import com.minhnbnt.fashionstore.dto.payment.MarkRefundedRequest;
import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentTransactionResponse;
import com.minhnbnt.fashionstore.dto.payment.SimulateVietQrRequest;
import com.minhnbnt.fashionstore.dto.payment.UpdatePaymentGatewayRequest;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(
    name = "Admin - Payments",
    description = "Quản trị cấu hình cổng thanh toán (COD, VietQR), đối soát hoàn tiền và duyệt rút tiền của Shop."
)
public class AdminPaymentController {

    private final PaymentGatewayService gateways;

    private final RevenueService revenue;

    private final PaymentFlowService paymentFlow;

    @GetMapping("/payment-gateways")
    @Operation(
        operationId = "listPaymentGateways",
        summary = "Danh sách cấu hình cổng thanh toán",
        description = "Trả về cấu hình hiện hành của COD và VIETQR. Trường webhookSecret chỉ báo đã đặt hay chưa."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách cấu hình cổng thanh toán")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<PaymentGatewayResponse> listPaymentGateways() {
        return gateways.list();
    }

    @PutMapping("/payment-gateways/{code}")
    @Operation(
        operationId = "updatePaymentGateway",
        summary = "Cập nhật cấu hình cổng thanh toán",
        description = "Bật/tắt cổng COD hoặc VIETQR, cập nhật tài khoản ngân hàng nhận tiền của sàn, "
            + "khóa bí mật Webhook và chế độ Demo Sandbox. Không cho phép tắt cổng cuối cùng đang bật."
    )
    @ApiResponse(responseCode = "200", description = "Cấu hình cổng thanh toán sau cập nhật")
    @ApiResponse(responseCode = "400", description = "Mã BIN ngân hàng không hợp lệ hoặc dữ liệu sai định dạng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "409", description = "Tắt cổng cuối cùng hoặc bật VIETQR khi thiếu cấu hình ngân hàng")
    @SecurityRequirement(name = "bearerAuth")
    public PaymentGatewayResponse updatePaymentGateway(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Mã cổng thanh toán (COD hoặc VIETQR)", example = "VIETQR")
        @PathVariable PaymentMethod code,
        @Valid @RequestBody UpdatePaymentGatewayRequest request
    ) {
        return gateways.update(adminIdOf(jwt), code, request);
    }

    @GetMapping("/payments/withdrawals")
    @Operation(
        operationId = "listAdminWithdrawals",
        summary = "Danh sách yêu cầu rút tiền của các Shop",
        description = "Phân trang và lọc danh sách lệnh rút tiền của các Shop theo trạng thái."
    )
    @ApiResponse(responseCode = "200", description = "Trang danh sách lệnh rút tiền của Shop")
    @ApiResponse(responseCode = "400", description = "Trạng thái lọc không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<AdminWithdrawalResponse> listAdminWithdrawals(
        @Parameter(description = "Lọc theo trạng thái lệnh rút (REQUESTED, PROCESSING, COMPLETED, REJECTED)")
        @RequestParam(required = false) @Nullable String status,
        @Parameter(description = "Số trang (bắt đầu từ 0)")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số phần tử mỗi trang")
        @RequestParam(defaultValue = "20") int size
    ) {
        return revenue.listAdminWithdrawals(status, page, size);
    }

    @PatchMapping("/payments/withdrawals/{id}/approve")
    @Operation(
        operationId = "approveWithdrawal",
        summary = "Duyệt lệnh rút tiền của Shop",
        description = "Chuyển lệnh rút tiền từ REQUESTED sang COMPLETED và ghi lịch sử đối soát."
    )
    @ApiResponse(responseCode = "200", description = "Lệnh rút tiền đã được duyệt")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy lệnh rút tiền")
    @ApiResponse(responseCode = "409", description = "Lệnh rút tiền không ở trạng thái REQUESTED")
    @SecurityRequirement(name = "bearerAuth")
    public AdminWithdrawalResponse approveWithdrawal(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID lệnh rút tiền")
        @PathVariable UUID id
    ) {
        return revenue.approveWithdrawal(adminIdOf(jwt), id);
    }

    @PatchMapping("/payments/withdrawals/{id}/reject")
    @Operation(
        operationId = "rejectWithdrawal",
        summary = "Từ chối lệnh rút tiền của Shop",
        description = "Chuyển lệnh rút tiền từ REQUESTED sang REJECTED và hoàn trả lại số dư khả dụng cho Shop."
    )
    @ApiResponse(responseCode = "200", description = "Lệnh rút tiền đã bị từ chối")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy lệnh rút tiền")
    @ApiResponse(responseCode = "409", description = "Lệnh rút tiền không ở trạng thái REQUESTED")
    @SecurityRequirement(name = "bearerAuth")
    public AdminWithdrawalResponse rejectWithdrawal(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID lệnh rút tiền")
        @PathVariable UUID id
    ) {
        return revenue.rejectWithdrawal(adminIdOf(jwt), id);
    }

    @GetMapping("/payments/transactions")
    @Operation(
        operationId = "listPaymentTransactions",
        summary = "Danh sách giao dịch thanh toán VietQR và đối soát hoàn tiền",
        description = "Phân trang, lọc theo trạng thái giao dịch, trạng thái hoàn tiền "
            + "và tìm kiếm theo mã giao dịch hoặc mã đơn hàng."
    )
    @ApiResponse(responseCode = "200", description = "Trang danh sách giao dịch thanh toán")
    @ApiResponse(responseCode = "400", description = "Tham số lọc không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<PaymentTransactionResponse> listPaymentTransactions(
        @Parameter(description = "Lọc theo trạng thái giao dịch (PENDING, SUCCESS, UNDERPAID, FAILED, EXPIRED)")
        @RequestParam(required = false) @Nullable PaymentTransactionStatus status,
        @Parameter(description = "Lọc theo trạng thái hoàn tiền (NONE, PENDING, REFUNDED)")
        @RequestParam(required = false) @Nullable RefundStatus refundStatus,
        @Parameter(description = "Tìm theo mã giao dịch (txnRef), mã ngân hàng hoặc mã đơn hàng")
        @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Số trang (bắt đầu từ 0)")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số phần tử mỗi trang")
        @RequestParam(defaultValue = "20") int size
    ) {
        return paymentFlow.searchTransactionsForAdmin(status, refundStatus, q, page, size);
    }

    @PostMapping("/payments/transactions/{id}/confirm-transfer")
    @Operation(
        operationId = "confirmPaymentTransfer",
        summary = "Xác nhận hoặc giả lập đã nhận tiền chuyển khoản VietQR",
        description = "Dành cho Admin đối soát thủ công sao kê ngân hàng hoặc giả lập biến động số dư "
            + "(nhận đủ 100% hoặc chuyển thiếu). Tự động kích hoạt đơn hàng sang PENDING/PAID nếu nhận đủ tiền."
    )
    @ApiResponse(responseCode = "200", description = "Giao dịch sau khi xác nhận nhận tiền")
    @ApiResponse(responseCode = "400", description = "Số tiền xác nhận không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy giao dịch")
    @ApiResponse(responseCode = "409", description = "Giao dịch đã được thanh toán trước đó")
    @SecurityRequirement(name = "bearerAuth")
    public PaymentTransactionResponse confirmPaymentTransfer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID giao dịch thanh toán")
        @PathVariable UUID id,
        @RequestBody(required = false) @Valid @Nullable SimulateVietQrRequest request
    ) {
        Long amount = null;
        if (request != null) {
            amount = request.amount();
        }
        return paymentFlow.confirmTransferByAdmin(adminIdOf(jwt), id, amount);
    }

    @PatchMapping("/payments/transactions/{id}/refunded")
    @Operation(
        operationId = "markTransactionRefunded",
        summary = "Xác nhận đã hoàn tiền cho giao dịch",
        description = "Chuyển trạng thái hoàn tiền của giao dịch từ PENDING sang REFUNDED, "
            + "đồng thời cập nhật trạng thái thanh toán của các đơn liên quan sang REFUNDED."
    )
    @ApiResponse(responseCode = "200", description = "Giao dịch đã được đánh dấu hoàn tiền")
    @ApiResponse(responseCode = "400", description = "Ghi chú đối soát không hợp lệ (5-500 ký tự)")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy giao dịch")
    @ApiResponse(responseCode = "409", description = "Giao dịch không ở trạng thái chờ hoàn tiền (PENDING)")
    @SecurityRequirement(name = "bearerAuth")
    public PaymentTransactionResponse markTransactionRefunded(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "ID giao dịch thanh toán")
        @PathVariable UUID id,
        @Valid @RequestBody MarkRefundedRequest request
    ) {
        return paymentFlow.markTransactionRefunded(adminIdOf(jwt), id, request.note());
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
