package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.settings.UpdateSettingsRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "test.isolation=SystemSettingsServiceTest")
class SystemSettingsServiceTest {

    private static final SystemSettings DEFAULTS =
        new SystemSettings(
            "nét", "1900 0000", "support@fashionstore.local", 500000, 2, 30000, 0, 15, 7, 0, 10000000
        );

    private static final Map<String, String> DEFAULT_ROWS = Map.ofEntries(
        Map.entry("site.name", "nét"),
        Map.entry("site.hotline", "1900 0000"),
        Map.entry("site.support_email", "support@fashionstore.local"),
        Map.entry("shipping.free_threshold", "500000"),
        Map.entry("shipping.base_items", "2"),
        Map.entry("shipping.base_fee", "30000"),
        Map.entry("shipping.extra_item_fee", "0"),
        Map.entry("order.payment_hold_minutes", "15"),
        Map.entry("order.auto_complete_days", "7"),
        Map.entry("order.cod_min_total", "0"),
        Map.entry("order.cod_max_total", "10000000")
    );

    @Autowired
    private SystemSettingsService settings;

    @Autowired
    private ConfigAuditService audits;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbc;

    private UUID adminId;

    @BeforeEach
    void resetSettings() {
        jdbc.sql("DELETE FROM admin_config_audit").update();
        DEFAULT_ROWS.forEach((key, value) -> jdbc.sql("UPDATE system_setting SET value = :v WHERE key = :k")
            .param("v", value)
            .param("k", key)
            .update());

        adminId = UUID.randomUUID();
        users.upsert(adminId, new CreateUserRequest("admin-" + adminId + "@t.local", "Admin"));
    }

    private static UpdateSettingsRequest only(
        @Nullable Long freeShippingThreshold,
        @Nullable Long codMin,
        @Nullable Long codMax
    ) {
        return new UpdateSettingsRequest(
            null, null, null, freeShippingThreshold, null, null, null, null, null, codMin, codMax
        );
    }

    @Test
    void shippingFeeSettingsAreUpdatedAndAudited() {
        settings.update(
            adminId,
            new UpdateSettingsRequest(null, null, null, null, null, 25000L, 4000L, null, null, null, null)
        );

        var current = settings.current();
        assertThat(current.shippingBaseFee()).isEqualTo(25000);
        assertThat(current.shippingExtraItemFee()).isEqualTo(4000);

        var keys = audits.page(ConfigAuditEntity.SETTING, 0, 20).items().stream()
            .map(row -> row.entityKey())
            .toList();
        assertThat(keys).containsExactlyInAnyOrder("shipping.base_fee", "shipping.extra_item_fee");
    }

    @Test
    void currentReturnsSeededDefaults() {
        assertThat(settings.current()).isEqualTo(DEFAULTS);
    }

    @Test
    void partialUpdateChangesOnlyGivenKeysAndAudits() {
        settings.update(adminId, only(300000L, null, null));

        var current = settings.current();
        assertThat(current.freeShippingThreshold()).isEqualTo(300000);
        assertThat(current.siteName()).isEqualTo("nét");

        var page = audits.page(ConfigAuditEntity.SETTING, 0, 20);
        assertThat(page.items()).hasSize(1);
        var row = page.items().getFirst();
        assertThat(row.entityKey()).isEqualTo("shipping.free_threshold");
        assertThat(row.oldValue()).contains("500000");
        assertThat(row.newValue()).contains("300000");
    }

    @Test
    void unchangedValueIsNotAudited() {
        settings.update(adminId, only(500000L, null, null));

        assertThat(audits.page(ConfigAuditEntity.SETTING, 0, 20).items()).isEmpty();
    }

    @Test
    void codMaxBelowMinIsRejected() {
        assertThatThrownBy(() -> settings.update(adminId, only(300000L, 200000L, 100000L)))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );

        assertThat(settings.current()).isEqualTo(DEFAULTS);
    }

    @Test
    void codMaxZeroMeansUnlimitedIsAccepted() {
        settings.update(adminId, only(null, 200000L, 0L));

        assertThat(settings.current().codMinTotal()).isEqualTo(200000);
        assertThat(settings.current().codMaxTotal()).isZero();
    }
}
