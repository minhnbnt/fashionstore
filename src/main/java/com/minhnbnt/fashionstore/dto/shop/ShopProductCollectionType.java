package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Loại bộ sưu tập nổi bật của shop")
public enum ShopProductCollectionType {
    NEW_ARRIVALS,
    BEST_SELLER,
    SALE_OFF
}
