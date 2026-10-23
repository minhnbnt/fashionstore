package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminShopResponse(
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

    @Schema(example = "PENDING")
    String status,

    @Schema(description = "Lý do từ chối/tạm khóa gần nhất", nullable = true)
    @Nullable
    String statusReason,

    @Schema(description = "id owner, null nếu shop mất owner", nullable = true)
    @Nullable
    UUID ownerId,

    @Schema(nullable = true, example = "an@t.local")
    @Nullable
    String ownerEmail,

    @Schema(nullable = true, example = "An")
    @Nullable
    String ownerName,

    @Schema(description = "Trạng thái tài khoản owner", nullable = true, example = "ACTIVE")
    @Nullable
    String ownerStatus,

    @Schema(description = "Lần nộp hồ sơ gần nhất, hàng đợi duyệt xếp theo cột này")
    Instant submittedAt,

    @Schema(nullable = true)
    @Nullable
    Instant approvedAt,

    Instant createdAt,

    Instant updatedAt,

    @Schema(description = "Số điện thoại kho", nullable = true)
    @Nullable
    String phone,

    @Schema(description = "Địa chỉ lấy hàng", nullable = true)
    @Nullable
    String address,

    @Schema(description = "Họ tên thật chủ shop theo CCCD", nullable = true)
    @Nullable
    String legalName,

    @Schema(description = "Số CCCD/CMND", nullable = true)
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

    @Schema(description = "S3 key giấy chứng nhận ĐKKD (nếu có)", nullable = true)
    @Nullable
    String businessCertKey,

    @Schema(description = "Tên ngân hàng thụ hưởng", nullable = true)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng", nullable = true)
    @Nullable
    String bankAccount,

    @Schema(description = "Tên chủ tài khoản ngân hàng", nullable = true)
    @Nullable
    String bankHolder,

    @Schema(description = "Trạng thái định danh eKYC: PENDING_REVIEW, VERIFIED, REJECTED", example = "PENDING_REVIEW")
    String kycStatus,

    @Schema(description = "Lý do từ chối định danh nếu có", nullable = true)
    @Nullable
    String kycRejectionReason
) {
}
