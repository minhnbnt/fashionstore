package com.minhnbnt.fashionstore.model;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Danh sách tag đánh giá nhanh chuẩn hóa cho chức năng 1.5.1.
 */
public final class ReviewTag {

    public static final String QUALITY_GOOD = "Chất lượng tốt";
    public static final String AS_DESCRIBED = "Đúng mô tả";
    public static final String FAST_DELIVERY = "Giao hàng nhanh";
    public static final String CAREFUL_PACKING = "Đóng gói cẩn thận";
    public static final String TRUE_TO_SIZE = "Size đúng chuẩn";

    public static final Set<String> ALLOWED_TAGS = Set.of(
        QUALITY_GOOD,
        AS_DESCRIBED,
        FAST_DELIVERY,
        CAREFUL_PACKING,
        TRUE_TO_SIZE
    );

    private ReviewTag() {
    }

    /**
     * Kiểm tra và chuẩn hóa danh sách tag đánh giá nhanh theo whitelist, loại trùng giữ nguyên thứ tự.
     */
    public static String[] normalizeAndValidate(@Nullable List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new String[0];
        }
        var normalized = new LinkedHashSet<String>();
        for (var raw : rawTags) {
            var trimmed = raw.trim();
            if (!ALLOWED_TAGS.contains(trimmed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review tag: " + raw);
            }
            normalized.add(trimmed);
        }
        return normalized.toArray(String[]::new);
    }
}

