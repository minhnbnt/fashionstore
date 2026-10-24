package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ShopResponse(
    UUID id,

    String name,

    String slug,

    String description,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "S3 key, frontend tự ghép base URL", nullable = true)
    @Nullable
    String bannerKey,

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

    @Schema(description = "Họ tên thật chủ shop theo CCCD", nullable = true)
    @Nullable
    String legalName,

    @Schema(description = "Số CCCD", nullable = true)
    @Nullable
    String idNumber,

    @Schema(description = "Ngày cấp CCCD", nullable = true)
    @Nullable
    String idIssuedDate,

    @Schema(description = "Nơi cấp CCCD", nullable = true)
    @Nullable
    String idIssuedPlace,

    @Schema(description = "Mã số thuế", nullable = true)
    @Nullable
    String taxCode,

    @Schema(description = "S3 key ảnh mặt trước CCCD", nullable = true)
    @Nullable
    String idFrontKey,

    @Schema(description = "S3 key ảnh mặt sau CCCD", nullable = true)
    @Nullable
    String idBackKey,

    @Schema(description = "S3 key ảnh selfie cầm CCCD", nullable = true)
    @Nullable
    String idSelfieKey,

    @Schema(description = "S3 key giấy chứng nhận ĐKKD", nullable = true)
    @Nullable
    String businessCertKey,

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
