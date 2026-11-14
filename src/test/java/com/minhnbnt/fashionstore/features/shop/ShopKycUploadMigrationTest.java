package com.minhnbnt.fashionstore.features.shop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

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
 * Migration test V39 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V36 (7 cột key của {@code shop} còn nguyên), migrate qua V37 (backfill
 * upload) rồi V39 (đổi {@code *_upload_id}), khẳng định data cũ map đúng sang FK, cột
 * key bị drop, NULL giữ nguyên và FK chặn xoá upload đang được tham chiếu.
 */
@Testcontainers
class ShopKycUploadMigrationTest {

    private static final String LOGO_KEY = "b1111111-1111-1111-1111-111111111111.webp";
    private static final String BANNER_KEY = "b2222222-2222-2222-2222-222222222222.webp";
    private static final String ID_DOC_KEY = "b3333333-3333-3333-3333-333333333333.webp";
    private static final String CERT_KEY = "b4444444-4444-4444-4444-444444444444.pdf";
    private static final String FRONT_KEY = "b5555555-5555-5555-5555-555555555555.webp";
    private static final String BACK_KEY = "b6666666-6666-6666-6666-666666666666.webp";
    private static final String SELFIE_KEY = "b7777777-7777-7777-7777-777777777777.webp";

    private static final String SHOP_SLUG = "kyc-legacy-shop";
    private static final String PLAIN_SLUG = "kyc-plain-shop";

    private static final Map<String, String> OLD_TO_NEW = Map.of(
        "logo_key", "logo_upload_id",
        "banner_key", "banner_upload_id",
        "id_doc_key", "id_doc_upload_id",
        "business_cert_key", "business_cert_upload_id",
        "id_front_key", "id_front_upload_id",
        "id_back_key", "id_back_upload_id",
        "id_selfie_key", "id_selfie_upload_id"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("ghcr.io/minhnbnt/postgresql:latest")
            .asCompatibleSubstituteFor("postgres"))
        .withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll
    static void migrateSeedAndMigrateAgain() throws SQLException {

        migrateTo(MigrationVersion.fromVersion("36"));
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

            statement.execute("""
                INSERT INTO shop (name, slug, logo_key, banner_key, id_doc_key,
                                  business_cert_key, id_front_key, id_back_key, id_selfie_key)
                VALUES ('Kyc Legacy Shop', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'),
                       ('Kyc Plain Shop', '%s', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """.formatted(
                SHOP_SLUG, LOGO_KEY, BANNER_KEY, ID_DOC_KEY, CERT_KEY,
                FRONT_KEY, BACK_KEY, SELFIE_KEY,
                PLAIN_SLUG));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnsAreDroppedAndFkColumnsAdded() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'shop'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id')
                """)) {

            var names = new StringBuilder();
            while (rs.next()) {
                names.append(rs.getString(1)).append('\n');
            }

            for (var oldColumn : OLD_TO_NEW.keySet()) {
                assertThat(names.toString()).doesNotContain(oldColumn);
            }
            for (var newColumn : OLD_TO_NEW.values()) {
                assertThat(names.toString()).contains(newColumn);
            }
        }
    }

    @Test
    void legacyKeysMapToBackfilledUploadRows() throws SQLException {

        var expectedKeys = Map.of(
            "logo_upload_id", LOGO_KEY,
            "banner_upload_id", BANNER_KEY,
            "id_doc_upload_id", ID_DOC_KEY,
            "business_cert_upload_id", CERT_KEY,
            "id_front_upload_id", FRONT_KEY,
            "id_back_upload_id", BACK_KEY,
            "id_selfie_upload_id", SELFIE_KEY
        );

        try (var connection = connection()) {
            for (var entry : expectedKeys.entrySet()) {

                try (var statement = connection.prepareStatement(
                    "SELECT u.s3_key FROM shop s JOIN upload u ON u.id = s." + entry.getKey()
                        + " WHERE s.slug = ?")) {

                    statement.setString(1, SHOP_SLUG);

                    try (var rs = statement.executeQuery()) {

                        assertThat(rs.next()).as(entry.getKey()).isTrue();
                        assertThat(rs.getString(1)).isEqualTo(entry.getValue());
                    }
                }
            }
        }
    }

    @Test
    void nullKeysStayNull() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT logo_upload_id, banner_upload_id, id_doc_upload_id, business_cert_upload_id,
                   id_front_upload_id, id_back_upload_id, id_selfie_upload_id
            FROM shop WHERE slug = ?
            """)) {

            statement.setString(1, PLAIN_SLUG);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                for (var column : OLD_TO_NEW.values()) {
                    assertThat(rs.getObject(column)).as(column).isNull();
                }
            }
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        assertThatThrownBy(() -> {
            try (var connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DELETE FROM upload WHERE s3_key = '" + LOGO_KEY + "'");
            }
        }).isInstanceOf(SQLException.class);
    }
}
