package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cách sắp xếp kết quả. RELEVANCE chỉ có nghĩa khi có từ khóa, thiếu từ khóa thì về NEWEST.")
public enum ProductSort {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC
}
