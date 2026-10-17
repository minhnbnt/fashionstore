package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.model.ProductViolationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BanProductRequest(
    @Schema(description = "Loại vi phạm", example = "COUNTERFEIT")
    @NotNull
    ProductViolationType violationType,

    @Schema(
        description = "Lý do hiển thị cho shop và lưu vào lịch sử kiểm duyệt",
        example = "Sản phẩm sử dụng logo thương hiệu khi chưa được ủy quyền"
    )
    @NotBlank
    @Size(min = 5, max = 500)
    String reason
) {
}
