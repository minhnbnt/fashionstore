package com.minhnbnt.fashionstore.dto.voucher;

import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherStatus;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SellerVoucherResponse(
    @Schema(description = "ID mã giảm giá")
    UUID id,

    @Schema(description = "ID gian hàng")
    UUID shopId,

    @Schema(description = "Mã giảm giá", example = "SUMMER15")
    String code,

    @Schema(description = "Tên chương trình mã giảm giá", example = "Chào hè rực rỡ")
    String name,

    @Schema(description = "Mô tả chương trình")
    String description,

    @Schema(description = "Hình thức giảm giá: FIXED_AMOUNT hoặc PERCENTAGE")
    VoucherDiscountType discountType,

    @Schema(description = "Giá trị giảm (VND hoặc %)")
    long discountValue,

    @Schema(description = "Trần giảm tối đa (VND) nếu là PERCENTAGE", nullable = true)
    @Nullable
    Long maxDiscountAmount,

    @Schema(description = "Đơn hàng tối thiểu (VND)")
    long minOrderSubtotal,

    @Schema(description = "Phạm vi sản phẩm áp dụng: ALL_PRODUCTS hoặc SPECIFIC_PRODUCTS")
    VoucherProductScope productScope,

    @Schema(description = "Chế độ hiển thị: PUBLIC hoặc PRIVATE")
    VoucherVisibility visibility,

    @Schema(description = "Tổng số lượt sử dụng tối đa")
    int usageLimit,

    @Schema(description = "Số lượt đã sử dụng")
    int usedCount,

    @Schema(description = "Giới hạn lượt dùng mỗi khách")
    int perBuyerLimit,

    @Schema(description = "Thời điểm bắt đầu hiệu lực")
    Instant startsAt,

    @Schema(description = "Thời điểm kết thúc hiệu lực")
    Instant endsAt,

    @Schema(description = "Trạng thái bật/tắt thủ công")
    boolean active,

    @Schema(description = "Trạng thái hiệu lực dẫn xuất tại thời điểm hiện tại")
    VoucherStatus status,

    @Schema(description = "Số lượng sản phẩm chỉ định (0 nếu ALL_PRODUCTS)")
    int productCount,

    @Schema(description = "Thời điểm tạo")
    Instant createdAt,

    @Schema(description = "Thời điểm cập nhật gần nhất")
    Instant updatedAt
) {
}
