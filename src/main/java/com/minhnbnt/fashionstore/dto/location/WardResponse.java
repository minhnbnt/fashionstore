package com.minhnbnt.fashionstore.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

public record WardResponse(
    @Schema(description = "Mã xã/phường 5 chữ số", example = "00004") String code,
    @Schema(description = "Tên xã/phường", example = "Phường Ba Đình") String name
) {
}
