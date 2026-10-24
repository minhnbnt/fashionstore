package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateShopRequest(
    @Schema(example = "Shop An")
    @NotBlank(message = "Shop name is required")
    @Size(max = 200, message = "Shop name must not exceed 200 characters")
    String name,

    @Schema(description = "Slug duy nhất toàn sàn, chữ thường + số + gạch ngang", example = "shop-an")
    @NotBlank(message = "Shop slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Shop slug must contain only lowercase letters, digits, and hyphens")
    String slug,

    @Schema(description = "Mô tả giới thiệu phong cách gian hàng")
    @NotBlank(message = "Shop description is required")
    @Size(max = 2000, message = "Shop description must not exceed 2000 characters")
    String description,

    @Schema(description = "S3 key logo gian hàng")
    @NotBlank(message = "Shop logo key is required")
    String logoKey,

    @Schema(description = "S3 key ảnh bìa gian hàng")
    @NotBlank(message = "Shop banner key is required")
    String bannerKey,

    @Schema(description = "Số điện thoại kho / hotline")
    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    String phone,

    @Schema(description = "Địa chỉ kho lấy hàng")
    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    String address,

    @Schema(description = "Loại hình người bán: INDIVIDUAL hoặc CORPORATE", example = "INDIVIDUAL", nullable = true)
    @Nullable
    @Pattern(regexp = "INDIVIDUAL|CORPORATE")
    String sellerType,

    @Schema(description = "Họ và tên thật của chủ shop theo CCCD", example = "NGUYEN HOANG HIEP")
    @NotBlank(message = "Legal name is required")
    @Size(max = 200, message = "Legal name must not exceed 200 characters")
    String legalName,

    @Schema(description = "Số Căn cước công dân (9 hoặc 12 số)", example = "001202001234")
    @NotBlank(message = "ID card number is required")
    @Pattern(regexp = "^\\d{9}$|^\\d{12}$", message = "ID card number must be 9 or 12 digits")
    String idNumber,

    @Schema(description = "Ngày cấp CCCD", example = "20/05/2021")
    @NotBlank(message = "ID issued date is required")
    @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "ID issued date must be in dd/MM/yyyy format")
    String idIssuedDate,

    @Schema(description = "Nơi cấp CCCD", example = "Cục Cảnh sát QLHC về TTXH")
    @NotBlank(message = "ID issued place is required")
    String idIssuedPlace,

    @Schema(description = "Mã số thuế cá nhân hoặc doanh nghiệp (nếu có)", nullable = true)
    @Nullable
    String taxCode,

    @Schema(description = "S3 key ảnh mặt trước CCCD")
    @NotBlank(message = "ID front image key is required")
    String idFrontKey,

    @Schema(description = "S3 key ảnh mặt sau CCCD")
    @NotBlank(message = "ID back image key is required")
    String idBackKey,

    @Schema(description = "S3 key ảnh chân dung cầm CCCD")
    @NotBlank(message = "ID selfie image key is required")
    String idSelfieKey,

    @Schema(description = "S3 key giấy chứng nhận ĐKKD (nếu có)", nullable = true)
    @Nullable
    String businessCertKey,

    @Schema(description = "Tên ngân hàng thụ hưởng", example = "Vietcombank")
    @NotBlank(message = "Bank name is required")
    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    String bankName,

    @Schema(description = "Số tài khoản ngân hàng", example = "0123456789")
    @NotBlank(message = "Bank account number is required")
    @Size(max = 50, message = "Bank account number must not exceed 50 characters")
    String bankAccount,

    @Schema(description = "Tên chủ tài khoản ngân hàng (phải khớp legalName)", example = "NGUYEN HOANG HIEP")
    @NotBlank(message = "Bank account holder name is required")
    @Size(max = 100, message = "Bank account holder name must not exceed 100 characters")
    String bankHolder
) {
}
