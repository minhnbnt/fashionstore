package com.minhnbnt.fashionstore.model;

import org.jspecify.annotations.Nullable;

public enum ShopPermission {
    PRODUCT_READ,
    PRODUCT_WRITE,
    INVENTORY_READ,
    INVENTORY_WRITE,
    ORDER_READ,
    ORDER_PROCESS,
    ORDER_CANCEL,
    RETURN_PROCESS,
    REVENUE_READ,
    REVENUE_WITHDRAW,
    REVIEW_READ,
    REVIEW_REPLY,
    CHAT_ACCESS,
    DECORATION_WRITE;

    public static @Nullable ShopPermission parse(String raw) {
        for (var permission : values()) {
            if (permission.name().equals(raw)) {
                return permission;
            }
        }

        return null;
    }
}
