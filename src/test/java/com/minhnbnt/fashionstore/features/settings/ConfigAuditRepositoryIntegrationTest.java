package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.settings.ConfigAuditResponse;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghim hành vi SQL của ConfigAuditRepository: jsonb roundtrip dạng text, filter theo entity,
 * sort changed_at DESC, phân trang LIMIT/OFFSET. Bảng được nhiều test ghi chung nên assertion
 * scope theo key sinh riêng, không giả định bảng rỗng.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConfigAuditRepositoryIntegrationTest {

    @Autowired
    private ConfigAuditRepository audits;

    @Autowired
    private UserService users;

    private UUID admin() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    @Test
    void insertRoundtripsJsonbAndFiltersByEntity() {
        var settingKey = "audit-test-setting-" + UUID.randomUUID();
        var carrierKey = "audit-test-carrier-" + UUID.randomUUID();
        var changedBy = admin();

        audits.insert(ConfigAuditEntity.SETTING, settingKey, "UPDATE", "\"500000\"", "\"300000\"", changedBy);
        audits.insert(ConfigAuditEntity.CARRIER, carrierKey, "CREATE", null, "\"GHN\"", changedBy);

        var all = audits.search(null, 0, 100);

        var settingRow = byKey(all, settingKey);
        assertThat(settingRow.entity()).isEqualTo(ConfigAuditEntity.SETTING);
        assertThat(settingRow.action()).isEqualTo("UPDATE");
        assertThat(settingRow.oldValue()).isEqualTo("\"500000\"");
        assertThat(settingRow.newValue()).isEqualTo("\"300000\"");
        assertThat(settingRow.changedBy()).isEqualTo(changedBy);

        var carrierRow = byKey(all, carrierKey);
        assertThat(carrierRow.oldValue()).isNull();
        assertThat(carrierRow.newValue()).isEqualTo("\"GHN\"");

        var settingsOnly = audits.search(ConfigAuditEntity.SETTING, 0, 100);
        assertThat(keys(settingsOnly)).contains(settingKey).doesNotContain(carrierKey);

        var carriersOnly = audits.search(ConfigAuditEntity.CARRIER, 0, 100);
        assertThat(keys(carriersOnly)).contains(carrierKey).doesNotContain(settingKey);

        assertThat(all).isSortedAccordingTo(Comparator.comparing(ConfigAuditResponse::changedAt).reversed());
    }

    @Test
    void pagingPartitionsAllRowsAndCountMatchesTotal() {
        for (var i = 0; i < 3; i++) {
            audits.insert(ConfigAuditEntity.SETTING, "audit-test-page-" + i + "-" + UUID.randomUUID(), "UPDATE", null, "\"1\"", admin());
        }

        var all = audits.search(null, 0, 100);

        var page0 = audits.search(null, 0, 2);
        var page1 = audits.search(null, 1, 2);

        var expectedPage0 = all.subList(0, Math.min(2, all.size()));
        assertThat(page0).isEqualTo(expectedPage0);

        if (all.size() > 2) {
            var expectedPage1 = all.subList(2, Math.min(4, all.size()));
            assertThat(page1).isEqualTo(expectedPage1);
        } else {
            assertThat(page1).isEmpty();
        }

        assertThat(audits.count(null)).isEqualTo(all.size());
    }

    private static ConfigAuditResponse byKey(List<ConfigAuditResponse> rows, String entityKey) {
        return rows.stream()
            .filter(r -> r.entityKey().equals(entityKey))
            .findFirst()
            .orElseThrow();
    }

    private static List<String> keys(List<ConfigAuditResponse> rows) {
        return rows.stream().map(ConfigAuditResponse::entityKey).toList();
    }
}
