package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidaySyncResultResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidayYearResponse;
import com.minhnbnt.fashionstore.dto.holiday.SaveHolidayRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/holidays")
@RequiredArgsConstructor
@Tag(
    name = "Admin - Holidays",
    description = "Lịch nghỉ lễ dùng cho SLA đơn hàng, đồng bộ từ Calendarific, admin sửa tay. Cần role admin."
)
public class AdminHolidayController {

    private final AdminHolidayService adminHolidays;

    @GetMapping
    @Operation(
        operationId = "listHolidays", summary = "Lịch nghỉ lễ theo năm",
        description = "Mọi ngày của năm kể cả ngày đã bỏ, lần đồng bộ gần nhất, đã cấu hình API key chưa."
    )
    @ApiResponse(responseCode = "200", description = "Lịch của năm")
    @ApiResponse(responseCode = "400", description = "Năm ngoài 2000–2100")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public HolidayYearResponse list(
        @Parameter(description = "Năm, bỏ trống = năm nay (giờ VN)", example = "2026")
        @RequestParam(required = false) @Nullable @Min(2000) @Max(2100) Integer year
    ) {
        return adminHolidays.listYear(yearOrCurrent(year));
    }

    @PutMapping("/{day}")
    @Operation(
        operationId = "saveHoliday", summary = "Thêm/sửa ngày lễ",
        description = "Ghi tay, nguồn MANUAL: đồng bộ Calendarific không bao giờ ghi đè. Ngày đã bỏ được bật lại."
    )
    @ApiResponse(responseCode = "200", description = "Ngày đã lưu")
    @ApiResponse(responseCode = "400", description = "Thiếu tên/loại hoặc ngày sai định dạng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public HolidayResponse save(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Ngày, yyyy-MM-dd", example = "2027-04-16")
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
        @Valid @RequestBody SaveHolidayRequest request
    ) {
        return adminHolidays.save(adminIdOf(jwt), day, request.name(), request.kind());
    }

    @DeleteMapping("/{day}")
    @Operation(
        operationId = "disableHoliday", summary = "Bỏ ngày lễ",
        description = "Không xóa hẳn: đánh dấu đã bỏ (nguồn MANUAL) để lần đồng bộ sau không thêm lại. "
            + "Ngày đó tính là ngày làm việc trong SLA."
    )
    @ApiResponse(responseCode = "200", description = "Ngày đã bỏ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Ngày chưa có trong lịch")
    @SecurityRequirement(name = "bearerAuth")
    public HolidayResponse disable(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Ngày, yyyy-MM-dd", example = "2026-02-14")
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day
    ) {
        return adminHolidays.disable(adminIdOf(jwt), day);
    }

    @PostMapping("/sync")
    @Operation(
        operationId = "syncHolidays", summary = "Đồng bộ Calendarific ngay",
        description = "Lấy ngày nghỉ quốc gia + ngày đi làm bù của Việt Nam trong năm. Ngày admin sửa tay giữ nguyên, "
            + "ngày đồng bộ trước mà Calendarific rút lại thì xóa. Tốn 1 lượt gọi (gói free 500 lượt/tháng)."
    )
    @ApiResponse(responseCode = "200", description = "Kết quả đồng bộ")
    @ApiResponse(responseCode = "400", description = "Năm ngoài 2000–2100")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "502", description = "Calendarific lỗi hoặc hết lượt gọi")
    @ApiResponse(responseCode = "503", description = "Chưa cấu hình API key Calendarific")
    @SecurityRequirement(name = "bearerAuth")
    public HolidaySyncResultResponse sync(
        @Parameter(description = "Năm, bỏ trống = năm nay (giờ VN)", example = "2027")
        @RequestParam(required = false) @Nullable @Min(2000) @Max(2100) Integer year
    ) {
        return adminHolidays.sync(yearOrCurrent(year));
    }

    private int yearOrCurrent(@Nullable Integer year) {
        if (year == null) {
            return adminHolidays.currentYear();
        }
        return year;
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
