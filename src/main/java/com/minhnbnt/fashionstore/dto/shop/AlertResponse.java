package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cảnh báo vận hành cần xử lý của Shop")
public record AlertResponse(
    @Schema(description = "Loại cảnh báo (PENDING_ORDER, OUT_OF_STOCK, PENDING_RETURN)", example = "OUT_OF_STOCK")
    String type,

    @Schema(description = "Tiêu đề hoặc nội dung cảnh báo",
            example = "3 sản phẩm đã hết hàng trong kho")
    String message,

    @Schema(description = "Số lượng liên quan", example = "3")
    int count,

    @Schema(description = "Mức độ nghiêm trọng (INFO, WARNING, DANGER)", example = "WARNING")
    String severity
) {}
