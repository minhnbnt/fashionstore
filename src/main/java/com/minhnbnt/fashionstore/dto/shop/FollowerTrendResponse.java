package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Xu hướng người theo dõi Shop")
public record FollowerTrendResponse(
    @Schema(description = "Tổng số người theo dõi hiện tại", example = "1250")
    long totalFollowers,

    @Schema(description = "Thay đổi so với tháng trước (tăng/giảm)", example = "150")
    long followerChange
) {}
