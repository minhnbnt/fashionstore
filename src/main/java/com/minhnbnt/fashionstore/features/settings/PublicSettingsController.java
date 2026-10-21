package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.dto.settings.PublicSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/settings")
@RequiredArgsConstructor
@Tag(name = "Public - Settings", description = "Cấu hình chung hiển thị cho khách. Không cần đăng nhập.")
public class PublicSettingsController {

    private final SystemSettingsService settings;

    @GetMapping
    @Operation(
        operationId = "getPublicSettings",
        summary = "Cấu hình chung công khai",
        description = "Tên sàn, hotline, email hỗ trợ, ngưỡng miễn phí vận chuyển và số sản phẩm tính phí cơ bản."
    )
    @ApiResponse(responseCode = "200", description = "Cấu hình công khai")
    public PublicSettingsResponse get() {
        return settings.publicView();
    }
}
