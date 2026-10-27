package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kiểu sắp xếp sản phẩm trong trang shop")
public enum ShopProductSort {
    NEWEST,
    BEST_SELLER,
    PRICE_ASC,
    PRICE_DESC
}
