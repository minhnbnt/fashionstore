package com.minhnbnt.fashionstore.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PageResponse<T>(
    @Schema(description = "Phần tử của trang hiện tại")
    List<T> items,

    @Schema(description = "Tổng số phần tử khớp bộ lọc, không tính phân trang")
    long total,

    @Schema(description = "Trang hiện tại, đếm từ 0")
    int page,

    @Schema(description = "Số phần tử mỗi trang")
    int size
) {
}
