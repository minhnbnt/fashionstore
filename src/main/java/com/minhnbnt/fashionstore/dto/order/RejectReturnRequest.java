package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RejectReturnRequest(
    @Schema(description = "Lý do từ chối", example = "Sản phẩm không có dấu hiệu hư hỏng như phản ánh")
    @NotBlank
    String sellerNote
) {
}
