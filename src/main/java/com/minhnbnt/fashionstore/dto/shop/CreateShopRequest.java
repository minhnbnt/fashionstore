package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateShopRequest(
    @Schema(example = "Shop An")
    @NotBlank
    @Size(max = 200)
    String name,

    @Schema(description = "Slug duy nhất toàn sàn, chữ thường + số + gạch ngang", example = "shop-an")
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$")
    String slug,

    @Schema(nullable = true)
    @Size(max = 2000)
    @Nullable
    String description,

    @Schema(description = "S3 key logo, null nếu chưa có", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "S3 key banner, null nếu chưa có", nullable = true)
    @Nullable
    String bannerKey,

    @Schema(description = "Số điện thoại kho / hotline", nullable = true)
    @Nullable
    String phone,

    @Schema(description = "Địa chỉ kho lấy hàng", nullable = true)
    @Nullable
    String address,

    @Schema(description = "Loại hình người bán: INDIVIDUAL hoặc CORPORATE", example = "INDIVIDUAL", nullable = true)
    @Nullable
    String sellerType,

    @Schema(description = "Họ và tên thật của chủ shop theo CCCD", example = "NGUYEN HOANG HIEP", nullable = true)
    @Nullable
    String legalName,

    @Schema(description = "Số Căn cước công dân (12 số)", example = "001202001234", nullable = true)
    @Nullable
    String idNumber,

    @Schema(description = "Ngày cấp CCCD", example = "20/05/2021", nullable = true)
    @Nullable
    String idIssuedDate,

    @Schema(description = "Nơi cấp CCCD", example = "Cục Cảnh sát QLHC về TTXH", nullable = true)
    @Nullable
    String idIssuedPlace,

    @Schema(description = "Mã số thuế cá nhân hoặc doanh nghiệp", nullable = true)
    @Nullable
    String taxCode,

    @Schema(description = "S3 key ảnh mặt trước CCCD", nullable = true)
    @Nullable
    String idFrontKey,

    @Schema(description = "S3 key ảnh mặt sau CCCD", nullable = true)
    @Nullable
    String idBackKey,

    @Schema(description = "S3 key ảnh chân dung cầm CCCD", nullable = true)
    @Nullable
    String idSelfieKey,

    @Schema(description = "S3 key giấy chứng nhận ĐKKD (nếu là doanh nghiệp)", nullable = true)
    @Nullable
    String businessCertKey,

    @Schema(description = "Tên ngân hàng thụ hưởng", example = "Vietcombank", nullable = true)
    @Nullable
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng", example = "0123456789", nullable = true)
    @Nullable
    String bankAccount,

    @Schema(description = "Tên chủ tài khoản ngân hàng (phải khớp legalName)", example = "NGUYEN HOANG HIEP", nullable = true)
    @Nullable
    String bankHolder
) {
    public CreateShopRequest(
        String name,
        String slug,
        @Nullable String description,
        @Nullable String logoKey,
        @Nullable String bannerKey
    ) {
        this(
            name, slug, description, logoKey, bannerKey,
            null, null, "INDIVIDUAL", null, null,
            null, null, null, null, null,
            null, null, null, null, null
        );
    }
}
