package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.dto.location.ProvinceResponse;
import com.minhnbnt.fashionstore.dto.location.WardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/locations")
@RequiredArgsConstructor
@Tag(
    name = "Locations",
    description = "Danh mục tỉnh/thành và xã/phường sau sáp nhập 2025. Công khai, không cần đăng nhập."
)
public class LocationController {

    private final LocationService locations;

    @GetMapping("/provinces")
    @Operation(
        operationId = "listProvinces", summary = "Danh sách tỉnh/thành",
        description = "34 tỉnh/thành kèm miền (NORTH, CENTRAL, SOUTH), sắp theo tên."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách tỉnh/thành")
    public List<ProvinceResponse> listProvinces() {
        return locations.listProvinces();
    }

    @GetMapping("/provinces/{provinceCode}/wards")
    @Operation(
        operationId = "listWards", summary = "Danh sách xã/phường của tỉnh",
        description = "Các xã/phường thuộc một tỉnh/thành, sắp theo tên."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách xã/phường")
    @ApiResponse(responseCode = "404", description = "Tỉnh/thành không tồn tại")
    public List<WardResponse> listWards(
        @Parameter(description = "Mã tỉnh/thành 2 chữ số", example = "01") @PathVariable String provinceCode
    ) {
        return locations.listWards(provinceCode);
    }
}
