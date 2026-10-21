package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record DismissReportsRequest(
    @Schema(description = "Ghi chú vì sao không vi phạm, tùy chọn", nullable = true, example = "Ảnh đúng thực tế")
    @Nullable
    @Size(max = 500)
    String note
) {
}
