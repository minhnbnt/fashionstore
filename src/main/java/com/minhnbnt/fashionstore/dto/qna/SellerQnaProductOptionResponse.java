package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SellerQnaProductOptionResponse(
    @Schema(description = "ID sản phẩm")
    UUID id,

    @Schema(description = "Tên sản phẩm")
    String name,

    @Schema(description = "Số lượng câu hỏi của sản phẩm")
    long questionCount
) {
}
