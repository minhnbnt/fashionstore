package com.minhnbnt.fashionstore.features.content;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration test V41 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V12 (site_banner.image_key/mobile_image_key + site_popup.image_key còn
 * nguyên), migrate qua V37 (backfill upload) rồi V41 (đổi {@code *_upload_id}), khẳng
 * định data cũ map đúng sang FK, cột key bị drop, NULL giữ nguyên và FK chặn xoá
 * upload đang được tham chiếu.
 */
@Testcontainers
class SiteContentUploadMigrationTest {

    private static final String IMAGE_KEY = "d1111111-1111-1111-1111-111111111111.webp";
    private static final String MOBILE_IMAGE_KEY = "d2222222-2222-2222-2222-222222222222.webp";
    private static final String POPUP_IMAGE_KEY = "d3333333-3333-3333-3333-333333333333.webp";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("ghcr.io/minhnbnt/postgresql:latest")
            .asCompatibleSubstituteFor("postgres"))
        .withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll
    static void migrateSeedAndMigrateAgain() throws SQLException {

        migrateTo(MigrationVersion.fromVersion("12"));
        seedLegacyData();
        migrateToLatest();
    }

    private static void migrateTo(MigrationVersion target) {

        baseFlyway().target(target).load().migrate();
    }

    private static void migrateToLatest() {

        baseFlyway().load().migrate();
    }

    private static FluentConfiguration baseFlyway() {

        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .outOfOrder(true);
    }

    private static void seedLegacyData() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement()) {

            // Banner đầy đủ media: ảnh desktop + ảnh mobile riêng.
            statement.execute("""
                INSERT INTO site_banner (id, placement, title, image_key, mobile_image_key)
                VALUES (gen_random_uuid(), 'HOME_CAROUSEL', 'Banner legacy', '%s', '%s')
                """.formatted(IMAGE_KEY, MOBILE_IMAGE_KEY));

            // Banner không có ảnh mobile (cột NULL).
            statement.execute("""
                INSERT INTO site_banner (id, placement, title, image_key)
                VALUES (gen_random_uuid(), 'HOME_STRIP', 'Banner mobile trống', '%s')
                """.formatted(IMAGE_KEY));

            // Popup có ảnh (CHECK title IS NOT NULL OR image_key IS NOT NULL thỏa qua ảnh).
            statement.execute("""
                INSERT INTO site_popup (id, name, image_key, audience, frequency)
                VALUES (gen_random_uuid(), 'Popup legacy', '%s', 'ALL', 'EVERY_VISIT')
                """.formatted(POPUP_IMAGE_KEY));

            // Popup chỉ có tiêu đề, không ảnh — cột image_key NULL.
            statement.execute("""
                INSERT INTO site_popup (id, name, title, audience, frequency)
                VALUES (gen_random_uuid(), 'Popup chỉ title', 'Giảm 50%', 'GUEST', 'ONCE')
                """);
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnsAreDroppedAndFkColumnsAdded() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var bannerColumns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'site_banner'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id')
                """)) {

            var names = new StringBuilder();
            while (bannerColumns.next()) {
                names.append(bannerColumns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("image_key").doesNotContain("mobile_image_key");
            assertThat(names.toString()).contains("image_upload_id").contains("mobile_image_upload_id");
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var popupColumns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'site_popup'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id')
                """)) {

            var names = new StringBuilder();
            while (popupColumns.next()) {
                names.append(popupColumns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("image_key");
            assertThat(names.toString()).contains("image_upload_id");
        }
    }

    @Test
    void legacyKeysMapToBackfilledUploadRows() throws SQLException {

        try (var connection = connection()) {

            var expectedKeys = List.of(IMAGE_KEY, MOBILE_IMAGE_KEY, POPUP_IMAGE_KEY);
            var tables = List.of("site_banner", "site_banner", "site_popup");
            var columns = List.of("image_upload_id", "mobile_image_upload_id", "image_upload_id");

            for (int i = 0; i < expectedKeys.size(); i++) {
                var expectedKey = expectedKeys.get(i);

                try (var statement = connection.prepareStatement(
                    "SELECT u.s3_key FROM " + tables.get(i)
                        + " t JOIN upload u ON u.id = t." + columns.get(i)
                        + " WHERE u.s3_key = ?")) {

                    statement.setString(1, expectedKey);

                    try (var rs = statement.executeQuery()) {

                        assertThat(rs.next()).as(columns.get(i) + " -> " + expectedKey).isTrue();
                        assertThat(rs.getString(1)).isEqualTo(expectedKey);
                    }
                }
            }
        }
    }

    @Test
    void nullKeysStayNull() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT t.mobile_image_upload_id, t.image_upload_id FROM site_banner t
                WHERE t.title = 'Banner mobile trống'
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject("mobile_image_upload_id")).isNull();
            assertThat(rs.getObject("image_upload_id")).isNotNull();
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT image_upload_id FROM site_popup WHERE name = 'Popup chỉ title'
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject("image_upload_id")).isNull();
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        var referencedKeys = List.of(IMAGE_KEY, MOBILE_IMAGE_KEY, POPUP_IMAGE_KEY);
        for (var key : referencedKeys) {

            var finalKey = key;
            assertThatThrownBy(() -> {
                try (var connection = connection(); var statement = connection.createStatement()) {
                    statement.execute("DELETE FROM upload WHERE s3_key = '" + finalKey + "'");
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void bannerImageUploadIdNotNullableAfterBackfill() throws SQLException {

        // site_banner.image_key cũ NOT NULL nên mọi banner đều backfill được image_upload_id.
        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT count(*) FROM site_banner WHERE image_upload_id IS NULL
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).isZero();
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'site_banner' AND column_name = 'image_upload_id'
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("NO");
        }
    }
}
