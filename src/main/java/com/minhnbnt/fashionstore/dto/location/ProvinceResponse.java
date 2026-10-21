package com.minhnbnt.fashionstore.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProvinceResponse(
    @Schema(description = "Mã tỉnh/thành 2 chữ số", example = "01") String code,
    @Schema(description = "Tên tỉnh/thành", example = "Thành phố Hà Nội") String name,
    @Schema(description = "Miền", allowableValues = {"NORTH", "CENTRAL", "SOUTH"}, example = "NORTH") String region
) {
}
