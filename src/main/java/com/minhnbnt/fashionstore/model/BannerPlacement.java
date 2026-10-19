package com.minhnbnt.fashionstore.model;

/**
 * Vị trí banner trên storefront. Tên trùng CHECK constraint {@code site_banner.placement}.
 */
public enum BannerPlacement {
    /** Carousel ngay dưới hero trang chủ, tỉ lệ 3:1. */
    HOME_CAROUSEL,
    /** Dải banner giữa trang chủ, tỉ lệ 4:1. */
    HOME_STRIP
}
