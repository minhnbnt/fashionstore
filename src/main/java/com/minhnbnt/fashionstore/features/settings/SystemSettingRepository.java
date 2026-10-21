package com.minhnbnt.fashionstore.features.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng key-value cấu hình chung (không có entity).
 */
@Repository
@RequiredArgsConstructor
public class SystemSettingRepository {

    private final JdbcClient jdbc;

    public Map<String, String> findAll() {
        return load("SELECT key, value FROM system_setting");
    }

    /**
     * Khóa toàn bộ dòng cấu hình để các lần cập nhật đồng thời được xử lý tuần tự.
     */
    public Map<String, String> findAllForUpdate() {
        return load("SELECT key, value FROM system_setting FOR UPDATE");
    }

    public void update(String key, String value, UUID updatedBy) {
        jdbc.sql("UPDATE system_setting SET value = :value, updated_at = now(), updated_by = :by WHERE key = :key")
            .param("value", value)
            .param("by", updatedBy)
            .param("key", key)
            .update();
    }

    private Map<String, String> load(String sql) {
        var result = new HashMap<String, String>();

        jdbc.sql(sql)
            .query((rs, rowNum) -> result.put(rs.getString("key"), rs.getString("value")))
            .list();

        return result;
    }
}
