package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteStaffRequest(
    @Schema(description = "Email nhân viên cần mời", example = "staff@example.com")
    @NotBlank
    @Email
    String email,

    @Schema(description = "Id vai trò gán cho nhân viên")
    @NotNull
    UUID roleId
) {
}
