package com.minhnbnt.fashionstore.dto.holiday;

import com.minhnbnt.fashionstore.model.HolidayKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveHolidayRequest(
    @Schema(example = "Nghỉ bù Quốc tế Lao động")
    @NotBlank
    @Size(max = 200)
    String name,

    @Schema(description = "HOLIDAY (nghỉ) | WORKING_DAY (đi làm bù)", example = "HOLIDAY")
    @NotNull
    HolidayKind kind
) {
}
