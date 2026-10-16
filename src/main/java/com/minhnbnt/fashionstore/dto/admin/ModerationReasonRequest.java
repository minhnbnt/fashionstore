package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerationReasonRequest(
    @Schema(
        description = "Lý do hiển thị cho shop/người dùng và lưu vào lịch sử kiểm duyệt",
        example = "Thông tin shop chưa đầy đủ, vui lòng bổ sung mô tả và logo"
    )
    @NotBlank
    @Size(min = 5, max = 500)
    String reason
) {
}
