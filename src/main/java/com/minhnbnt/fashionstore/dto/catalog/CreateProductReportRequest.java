package com.minhnbnt.fashionstore.dto.catalog;

import com.minhnbnt.fashionstore.model.ProductViolationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateProductReportRequest(
    @Schema(description = "Lý do báo cáo, dùng chung loại vi phạm với kiểm duyệt", example = "COUNTERFEIT")
    @NotNull
    ProductViolationType reason,

    @Schema(
        description = "Mô tả thêm cho admin, tối đa 1000 ký tự",
        nullable = true,
        example = "Logo in lệch, vải khác ảnh"
    )
    @Nullable
    @Size(max = 1000)
    String description
) {
}
