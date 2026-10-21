package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.dto.location.ProvinceResponse;
import com.minhnbnt.fashionstore.dto.location.WardResponse;
import com.minhnbnt.fashionstore.model.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Danh mục tỉnh/xã sau sáp nhập 2025 và xác thực cặp tỉnh - xã.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locations;

    @Transactional(readOnly = true)
    public List<ProvinceResponse> listProvinces() {
        return locations.findProvinces();
    }

    @Transactional(readOnly = true)
    public List<WardResponse> listWards(String provinceCode) {
        if (!locations.provinceExists(provinceCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Province not found");
        }

        return locations.findWards(provinceCode);
    }

    @Transactional(readOnly = true)
    public ResolvedLocation resolve(String provinceCode, String wardCode) {
        var province = requireProvince(provinceCode);
        var ward = locations.findWardInProvince(provinceCode, wardCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ward does not belong to province"));

        return new ResolvedLocation(
            province.code(), province.name(), Region.valueOf(province.region()), ward.code(), ward.name()
        );
    }

    @Transactional(readOnly = true)
    public Region regionOf(String provinceCode) {
        return Region.valueOf(requireProvince(provinceCode).region());
    }

    private ProvinceResponse requireProvince(String provinceCode) {
        return locations.findProvince(provinceCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown province"));
    }
}
