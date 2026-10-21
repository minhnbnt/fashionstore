package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.dto.location.ProvinceResponse;
import com.minhnbnt.fashionstore.dto.location.WardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read model danh mục địa giới hành chính (bảng seed, không có entity).
 */
@Repository
@RequiredArgsConstructor
public class LocationRepository {

    private final JdbcClient jdbc;

    public List<ProvinceResponse> findProvinces() {
        return jdbc.sql("SELECT code, name, region FROM province ORDER BY name")
            .query((rs, rowNum) -> new ProvinceResponse(rs.getString("code"), rs.getString("name"), rs.getString("region")))
            .list();
    }

    public Optional<ProvinceResponse> findProvince(String provinceCode) {
        return jdbc.sql("SELECT code, name, region FROM province WHERE code = :code")
            .param("code", provinceCode)
            .query((rs, rowNum) -> new ProvinceResponse(rs.getString("code"), rs.getString("name"), rs.getString("region")))
            .optional();
    }

    public boolean provinceExists(String provinceCode) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM province WHERE code = :code)")
            .param("code", provinceCode)
            .query(Boolean.class)
            .single();
    }

    public List<WardResponse> findWards(String provinceCode) {
        return jdbc.sql("SELECT code, name FROM ward WHERE province_code = :province ORDER BY name")
            .param("province", provinceCode)
            .query((rs, rowNum) -> new WardResponse(rs.getString("code"), rs.getString("name")))
            .list();
    }

    public Optional<WardResponse> findWardInProvince(String provinceCode, String wardCode) {
        return jdbc.sql("SELECT code, name FROM ward WHERE code = :ward AND province_code = :province")
            .param("ward", wardCode)
            .param("province", provinceCode)
            .query((rs, rowNum) -> new WardResponse(rs.getString("code"), rs.getString("name")))
            .optional();
    }
}
