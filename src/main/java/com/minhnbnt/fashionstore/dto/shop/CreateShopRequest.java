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

    @Schema(description = "Mô tả giới thiệu phong cách gian hàng")
    @NotBlank
    @Size(max = 2000)
    String description,

    @Schema(description = "S3 key logo gian hàng")
    @NotBlank
    String logoKey,

    @Schema(description = "S3 key ảnh bìa gian hàng")
    @NotBlank
    String bannerKey,

    @Schema(description = "Số điện thoại kho / hotline")
    @NotBlank
    @Size(max = 20)
    String phone,

    @Schema(description = "Địa chỉ kho lấy hàng")
    @NotBlank
    @Size(max = 500)
    String address,

    @Schema(description = "Loại hình người bán: INDIVIDUAL hoặc CORPORATE", example = "INDIVIDUAL", nullable = true)
    @Nullable
    @Pattern(regexp = "INDIVIDUAL|CORPORATE")
    String sellerType,

    @Schema(description = "Họ và tên thật của chủ shop theo CCCD", example = "NGUYEN HOANG HIEP")
    @NotBlank
    @Size(max = 200)
    String legalName,

    @Schema(description = "Số Căn cước công dân (9 hoặc 12 số)", example = "001202001234")
    @NotBlank
    String idNumber,

    @Schema(description = "Ngày cấp CCCD", example = "20/05/2021")
    @NotBlank
    @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$")
    String idIssuedDate,

    @Schema(description = "Nơi cấp CCCD", example = "Cục Cảnh sát QLHC về TTXH")
    @NotBlank
    String idIssuedPlace,

    @Schema(description = "Mã số thuế cá nhân hoặc doanh nghiệp (nếu có)", nullable = true)
    @Nullable
    String taxCode,

    @Schema(description = "S3 key ảnh mặt trước CCCD")
    @NotBlank
    String idFrontKey,

    @Schema(description = "S3 key ảnh mặt sau CCCD")
    @NotBlank
    String idBackKey,

    @Schema(description = "S3 key ảnh chân dung cầm CCCD")
    @NotBlank
    String idSelfieKey,

    @Schema(description = "S3 key giấy chứng nhận ĐKKD (nếu có)", nullable = true)
    @Nullable
    String businessCertKey,

    @Schema(description = "Tên ngân hàng thụ hưởng", example = "Vietcombank")
    @NotBlank
    @Size(max = 100)
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng", example = "0123456789")
    @NotBlank
    @Size(max = 50)
    String bankAccount,

    @Schema(description = "Tên chủ tài khoản ngân hàng (phải khớp legalName)", example = "NGUYEN HOANG HIEP")
    @NotBlank
    @Size(max = 100)
    String bankHolder
) {
}
