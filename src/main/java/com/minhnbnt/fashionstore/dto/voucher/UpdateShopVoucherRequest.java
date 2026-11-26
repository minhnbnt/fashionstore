package com.minhnbnt.fashionstore.dto.voucher;

import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateShopVoucherRequest(
    @Schema(description = "Mã giảm giá (4-20 ký tự chữ/số)", example = "SUMMER15")
    @NotBlank(message = "Voucher code is required")
    String code,

    @Schema(description = "Tên chương trình mã giảm giá", example = "Chào hè rực rỡ")
    @NotBlank(message = "Voucher name is required")
    @Size(max = 200, message = "Voucher name cannot exceed 200 characters")
    String name,

    @Schema(description = "Mô tả chi tiết chương trình", nullable = true)
    @Size(max = 1000, message = "Voucher description cannot exceed 1000 characters")
    @Nullable
    String description,

    @Schema(description = "Hình thức giảm giá: FIXED_AMOUNT hoặc PERCENTAGE", example = "PERCENTAGE")
    @NotNull(message = "Discount type is required")
    VoucherDiscountType discountType,

    @Schema(description = "Giá trị giảm (VND hoặc % từ 1-99)", example = "15")
    @Min(value = 1, message = "Discount value must be at least 1")
    long discountValue,

    @Schema(description = "Trần giảm tối đa (VND) cho loại PERCENTAGE", nullable = true, example = "50000")
    @Nullable
    Long maxDiscountAmount,

    @Schema(description = "Giá trị đơn hàng tối thiểu đủ điều kiện (VND)", example = "200000")
    @Min(value = 0, message = "Minimum order subtotal cannot be negative")
    long minOrderSubtotal,

    @Schema(
        description = "Phạm vi sản phẩm áp dụng: ALL_PRODUCTS hoặc SPECIFIC_PRODUCTS",
        example = "ALL_PRODUCTS"
    )
    @NotNull(message = "Product scope is required")
    VoucherProductScope productScope,

    @Schema(description = "Chế độ hiển thị: PUBLIC hoặc PRIVATE", example = "PUBLIC")
    @NotNull(message = "Visibility is required")
    VoucherVisibility visibility,

    @Schema(description = "Tổng số lượt sử dụng tối đa", example = "100")
    @Min(value = 1, message = "Usage limit must be at least 1")
    int usageLimit,

    @Schema(description = "Số lượt sử dụng tối đa cho mỗi người mua", example = "1")
    @Min(value = 1, message = "Per-buyer limit must be at least 1")
    int perBuyerLimit,

    @Schema(description = "Thời điểm bắt đầu hiệu lực")
    @NotNull(message = "Start time is required")
    Instant startsAt,

    @Schema(description = "Thời điểm kết thúc hiệu lực")
    @NotNull(message = "End time is required")
    Instant endsAt,

    @Schema(description = "Trạng thái kích hoạt của mã giảm giá", nullable = true, example = "true")
    @Nullable
    Boolean active,

    @Schema(description = "Danh sách ID sản phẩm áp dụng khi productScope = SPECIFIC_PRODUCTS", nullable = true)
    @Nullable
    List<UUID> productIds
) {
}
