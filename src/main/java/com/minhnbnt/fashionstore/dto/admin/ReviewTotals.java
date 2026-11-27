package com.minhnbnt.fashionstore.dto.admin;

/**
 * Tổng đánh giá toàn sàn; averageRating = 0 khi chưa có đánh giá nào.
 */
public record ReviewTotals(long count, double averageRating) {
}
