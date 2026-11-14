package com.minhnbnt.fashionstore.features.catalog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
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
 * Migration test V40 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V36 (product_image.s3_key + product.video_key/garment_key còn nguyên),
 * migrate qua V37 (backfill upload) rồi V40 (đổi {@code *_upload_id}), khẳng định data
 * cũ map đúng sang FK, cột key bị drop, NULL giữ nguyên và FK chặn xoá upload đang
 * được tham chiếu.
 */
@Testcontainers
class ProductUploadMigrationTest {

    private static final String IMAGE_KEY = "c1111111-1111-1111-1111-111111111111.webp";
    private static final String VARIANT_IMAGE_KEY = "c2222222-2222-2222-2222-222222222222.webp";
    private static final String VIDEO_KEY = "c3333333-3333-3333-3333-333333333333.mp4";
    private static final String GARMENT_KEY = "c4444444-4444-4444-4444-444444444444.webp";

    private static final String SHOP_SLUG = "product-legacy-shop";
    private static final String PLAIN_SLUG = "product-plain-shop";

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
                INSERT INTO shop (name, slug) VALUES ('Product Legacy Shop', '%s'),
                                                     ('Product Plain Shop', '%s')
                """.formatted(SHOP_SLUG, PLAIN_SLUG));

            // Shop đầy đủ media: ảnh chung + ảnh variant + video + garment.
            statement.execute("""
                INSERT INTO product (shop_id, name, slug, status, video_key, garment_key)
                SELECT id, 'Áo legacy', 'ao-legacy', 'ACTIVE', '%s', '%s' FROM shop WHERE slug = '%s'
                """.formatted(VIDEO_KEY, GARMENT_KEY, SHOP_SLUG));

            statement.execute("""
                INSERT INTO product_variant (shop_id, product_id, sku, size, color, price)
                SELECT p.shop_id, p.id, 'SKU-LEGACY', 'M', 'Đen', 100000
                FROM product p JOIN shop s ON s.id = p.shop_id WHERE s.slug = '%s'
                """.formatted(SHOP_SLUG));

            statement.execute("""
                INSERT INTO product_image (product_id, s3_key, sort_order)
                SELECT p.id, '%s', 0
                FROM product p JOIN shop s ON s.id = p.shop_id WHERE s.slug = '%s'
                """.formatted(IMAGE_KEY, SHOP_SLUG));

            statement.execute("""
                INSERT INTO product_image (product_id, variant_id, s3_key, sort_order)
                SELECT p.id, pv.id, '%s', 1
                FROM product p
                JOIN shop s ON s.id = p.shop_id
                JOIN product_variant pv ON pv.product_id = p.id
                WHERE s.slug = '%s'
                """.formatted(VARIANT_IMAGE_KEY, SHOP_SLUG));

            // Shop trống media: mọi cột key NULL.
            statement.execute("""
                INSERT INTO product (shop_id, name, slug, status)
                SELECT id, 'Áo trống', 'ao-trong', 'ACTIVE' FROM shop WHERE slug = '%s'
                """.formatted(PLAIN_SLUG));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnsAreDroppedAndFkColumnsAdded() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var productColumns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'product'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id')
                """)) {

            var names = new StringBuilder();
            while (productColumns.next()) {
                names.append(productColumns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("video_key").doesNotContain("garment_key");
            assertThat(names.toString()).contains("video_upload_id").contains("garment_upload_id");
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var imageColumns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'product_image'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id'
                       OR column_name = 'upload_id')
                """)) {

            var names = new StringBuilder();
            while (imageColumns.next()) {
                names.append(imageColumns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("s3_key");
            assertThat(names.toString()).contains("upload_id");
        }
    }

    @Test
    void legacyKeysMapToBackfilledUploadRows() throws SQLException {

        try (var connection = connection()) {

            // Ảnh chung + ảnh variant cùng map đúng upload theo s3_key.
            var imageKeys = List.of(IMAGE_KEY, VARIANT_IMAGE_KEY);
            for (var expectedKey : imageKeys) {

                try (var statement = connection.prepareStatement("""
                    SELECT u.s3_key FROM product_image pi JOIN upload u ON u.id = pi.upload_id
                    WHERE u.s3_key = ?
                    """)) {

                    statement.setString(1, expectedKey);

                    try (var rs = statement.executeQuery()) {

                        assertThat(rs.next()).as("product_image " + expectedKey).isTrue();
                        assertThat(rs.getString(1)).isEqualTo(expectedKey);
                    }
                }
            }

            var mediaColumns = Map.of(
                "video_upload_id", VIDEO_KEY,
                "garment_upload_id", GARMENT_KEY
            );
            for (var entry : mediaColumns.entrySet()) {

                try (var statement = connection.prepareStatement(
                    "SELECT u.s3_key FROM product p JOIN upload u ON u.id = p." + entry.getKey()
                        + " WHERE p.slug = 'ao-legacy'")) {

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
            SELECT video_upload_id, garment_upload_id FROM product WHERE slug = 'ao-trong'
            """)) {

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("video_upload_id")).isNull();
                assertThat(rs.getObject("garment_upload_id")).isNull();
            }
        }

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT pi.upload_id FROM product_image pi
            JOIN product p ON p.id = pi.product_id WHERE p.slug = 'ao-trong'
            """)) {

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        var referencedKeys = List.of(IMAGE_KEY, VIDEO_KEY, GARMENT_KEY);
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
    void productImageUploadIdNotNullableAfterBackfill() throws SQLException {

        // product_image.s3_key cũ NOT NULL nên mọi row đều backfill được upload_id.
        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT count(*) FROM product_image WHERE upload_id IS NULL
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).isZero();
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'product_image' AND column_name = 'upload_id'
                """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("NO");
        }
    }
}
