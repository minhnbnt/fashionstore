package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Hồ sơ shop cho seller (/mine) và khách (/{slug}). Không mang giấy tờ KYC —
 * dữ liệu định danh chỉ admin mới xem được (presigned GET qua AdminShopResponse).
 */
public record ShopResponse(
    UUID id,

    String name,

    String slug,

    String description,

    @Schema(description = "URL logo public path-style, null nếu chưa có. Không trả S3 key.")
    @Nullable
    String logoUrl,

    @Schema(description = "Id upload logo — FE echo lại khi cập nhật để giữ nguyên, null = chưa có")
    @Nullable
    UUID logoUploadId,

    @Schema(description = "URL ảnh bìa public path-style, null nếu chưa có. Không trả S3 key.")
    @Nullable
    String bannerUrl,

    @Schema(description = "Id upload ảnh bìa — FE echo lại khi cập nhật để giữ nguyên, null = chưa có")
    @Nullable
    UUID bannerUploadId,

    @Schema(example = "APPROVED")
    String status,

    @Schema(description = "Lý do từ chối/tạm khóa gần nhất, null khi PENDING/APPROVED", nullable = true)
    @Nullable
    String statusReason,

    @Schema(description = "Số điện thoại liên hệ", nullable = true)
    @Nullable
    String phone,

    @Schema(description = "Địa chỉ lấy hàng", nullable = true)
    @Nullable
    String address,

    @Schema(description = "Chính sách đổi trả", nullable = true)
    @Nullable
    String returnPolicy,

    int processingDays,

    String sellerType,

    @Schema(description = "Đánh giá trung bình", nullable = true)
    @Nullable
    Double rating,

    int ratingCount,

    int followerCount,

    @Schema(description = "Tên ngân hàng", nullable = true)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản", nullable = true)
    @Nullable
    String bankAccount,

    @Schema(description = "Chủ tài khoản", nullable = true)
    @Nullable
    String bankHolder,

    @Schema(description = "Tin nhắn tự động", nullable = true)
    @Nullable
    String autoReplyText,

    @Schema(description = "Giờ làm việc", nullable = true)
    @Nullable
    String workingHours,

    @Schema(description = "Trạng thái định danh eKYC: PENDING_REVIEW, VERIFIED, REJECTED", example = "PENDING_REVIEW")
    String kycStatus,

    @Schema(description = "Lý do từ chối định danh nếu có", nullable = true)
    @Nullable
    String kycRejectionReason,

    @Schema(description = "Shop uy tín (được duyệt sản phẩm tự động)")
    Boolean isTrusted,

    Instant createdAt
) {
}
