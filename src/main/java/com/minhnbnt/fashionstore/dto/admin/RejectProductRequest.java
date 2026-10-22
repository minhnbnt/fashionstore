package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectProductRequest(
    @Schema(
        description = "Lý do từ chối phê duyệt hiển thị cho shop",
        example = "Hình ảnh sản phẩm không đúng quy chuẩn (mờ, vi phạm bản quyền)"
    )
    @NotBlank
    @Size(min = 5, max = 500)
    String reason
) {
}
