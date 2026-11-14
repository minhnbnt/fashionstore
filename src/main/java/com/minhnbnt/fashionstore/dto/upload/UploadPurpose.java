package com.minhnbnt.fashionstore.dto.upload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mục đích upload. KYC đặt key dưới prefix kyc/ (chặn đọc ẩn danh); thiếu thì về MEDIA.")
public enum UploadPurpose {
    @JsonProperty("media") MEDIA,
    @JsonProperty("kyc") KYC
}
