package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMemberStatusRequest(
    @Schema(
        description = "Trạng thái mới của nhân viên",
        allowableValues = {"ACTIVE", "DEACTIVATED"},
        example = "DEACTIVATED"
    )
    @NotBlank
    @Pattern(regexp = "ACTIVE|DEACTIVATED")
    String status
) {
}
