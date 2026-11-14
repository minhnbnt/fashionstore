package com.minhnbnt.fashionstore.features.upload;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;

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

/**
 * Migration test V37 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V36 (mọi cột/mảng key còn nguyên) rồi migrate lên mới nhất, khẳng định
 * backfill dedupe vào {@code upload}, content-type suy từ extension và producer pgmq
 * enqueue message cho từng row (design D1 + D5).
 */
@Testcontainers
class UploadCentralizationMigrationTest {

    /** Dùng chung cho avatar và product_image để khẳng định dedupe theo key. */
    private static final String SHARED_KEY = "11111111-1111-1111-1111-111111111111.png";

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
                INSERT INTO user_profile (id, email, display_name, avatar_key)
                VALUES ('00000000-0000-0000-0000-0000000000a1', 'legacy-u1@t.local', 'Legacy Buyer', '%s')
                """.formatted(SHARED_KEY));

            statement.execute("""
                INSERT INTO shop (id, name, slug,
                    logo_key, banner_key, id_doc_key, business_cert_key,
                    id_front_key, id_back_key, id_selfie_key)
                VALUES ('00000000-0000-0000-0000-0000000000b1', 'Legacy Shop', 'legacy-shop',
                    '22222222-2222-2222-2222-222222222222.png',
                    '33333333-3333-3333-3333-333333333333.png',
                    '44444444-4444-4444-4444-444444444444.jpeg',
                    '55555555-5555-5555-5555-555555555555.pdf',
                    '66666666-6666-6666-6666-666666666666.jpeg',
                    '77777777-7777-7777-7777-777777777777.jpeg',
                    '88888888-8888-8888-8888-888888888888.jpeg')
                """);

            statement.execute("""
                INSERT INTO product (id, shop_id, name, slug, video_key, garment_key)
                VALUES ('00000000-0000-0000-0000-0000000000c1',
                    '00000000-0000-0000-0000-0000000000b1',
                    'Legacy Product', 'legacy-product',
                    '99999999-9999-9999-9999-999999999999.mp4',
                    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg')
                """);

            statement.execute("""
                INSERT INTO product_image (id, product_id, s3_key, sort_order)
                VALUES ('00000000-0000-0000-0000-0000000000d1',
                    '00000000-0000-0000-0000-0000000000c1', '%s', 0),
                    ('00000000-0000-0000-0000-0000000000d2',
                    '00000000-0000-0000-0000-0000000000c1',
                    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.webp', 1)
                """.formatted(SHARED_KEY));

            statement.execute("""
                INSERT INTO site_banner (id, placement, title, image_key, mobile_image_key)
                VALUES ('00000000-0000-0000-0000-0000000000e1', 'HOME_CAROUSEL', 'Legacy Banner',
                    'cccccccc-cccc-cccc-cccc-cccccccccccc.png',
                    'dddddddd-dddd-dddd-dddd-dddddddddddd.png')
                """);

            statement.execute("""
                INSERT INTO site_popup (id, name, title, image_key, audience, frequency)
                VALUES ('00000000-0000-0000-0000-0000000000e2', 'Legacy Popup', 'Hi',
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee.png', 'ALL', 'EVERY_VISIT')
                """);

            statement.execute("""
                INSERT INTO chat_conversation (id, shop_id, buyer_id)
                VALUES ('00000000-0000-0000-0000-0000000000f1',
                    '00000000-0000-0000-0000-0000000000b1',
                    '00000000-0000-0000-0000-0000000000a1')
                """);

            statement.execute("""
                INSERT INTO chat_message (id, conversation_id, sender_id, sender_type, content, image_key)
                VALUES ('00000000-0000-0000-0000-0000000000f2',
                    '00000000-0000-0000-0000-0000000000f1',
                    '00000000-0000-0000-0000-0000000000a1', 'BUYER', 'Xem giúp tôi',
                    'ffffffff-ffff-ffff-ffff-ffffffffffff.png')
                """);

            statement.execute("""
                INSERT INTO review (id, product_id, buyer_id, buyer_display_name, stars, image_keys, video_key)
                VALUES ('00000000-0000-0000-0000-000000000101',
                    '00000000-0000-0000-0000-0000000000c1',
                    '00000000-0000-0000-0000-0000000000a1', 'Legacy Buyer', 5,
                    ARRAY['12121212-1212-1212-1212-121212121212.png', '13131313-1313-1313-1313-131313131313.png'],
                    '14141414-1414-1414-1414-141414141414.webm')
                """);

            statement.execute("""
                INSERT INTO orders (id, code, shop_id, buyer_id, subtotal, shipping_fee, total,
                    shipping_name, shipping_phone, shipping_address)
                VALUES ('00000000-0000-0000-0000-000000000201', 'LEGACY-1',
                    '00000000-0000-0000-0000-0000000000b1',
                    '00000000-0000-0000-0000-0000000000a1', 0, 0, 0,
                    'Legacy Buyer', '0900000000', 'Ha Noi')
                """);

            statement.execute("""
                INSERT INTO return_request (id, order_id, shop_id, buyer_id, reason, evidence_keys)
                VALUES ('00000000-0000-0000-0000-000000000301',
                    '00000000-0000-0000-0000-000000000201',
                    '00000000-0000-0000-0000-0000000000b1',
                    '00000000-0000-0000-0000-0000000000a1', 'Sai size',
                    ARRAY['15151515-1515-1515-1515-151515151515.png', '16161616-1616-1616-1616-161616161616.png'])
                """);
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long scalar(String sql) throws SQLException {

        try (var connection = connection();
            var statement = connection.createStatement();
            var rs = statement.executeQuery(sql)) {

            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void backfillCreatesOneRowPerDistinctKey() throws SQLException {

        // 20 key distinct: avatar trùng product_image vẫn chỉ 1 row.
        assertThat(scalar("SELECT count(*) FROM upload")).isEqualTo(20L);

        var missing = scalar("""
            SELECT count(*) FROM upload u
            WHERE NOT EXISTS (
                SELECT 1 FROM pgmq.q_upload_gc q WHERE q.message ->> 's3_key' = u.s3_key)
            """);

        assertThat(missing).isZero();
    }

    @Test
    void contentTypeIsInferredFromKeyExtension() throws SQLException {

        try (var connection = connection();
            var statement = connection.prepareStatement("SELECT content_type FROM upload WHERE s3_key = ?")) {

            assertThat(contentType(statement, SHARED_KEY)).isEqualTo("image/png");
            assertThat(contentType(statement, "44444444-4444-4444-4444-444444444444.jpeg")).isEqualTo("image/jpeg");
            assertThat(contentType(statement, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg")).isEqualTo("image/jpeg");
            assertThat(contentType(statement, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.webp")).isEqualTo("image/webp");
            assertThat(contentType(statement, "55555555-5555-5555-5555-555555555555.pdf"))
                .isEqualTo("application/octet-stream");
            assertThat(contentType(statement, "99999999-9999-9999-9999-999999999999.mp4")).isEqualTo("video/mp4");
            assertThat(contentType(statement, "14141414-1414-1414-1414-141414141414.webm")).isEqualTo("video/webm");
        }
    }

    private static String contentType(PreparedStatement statement, String key) throws SQLException {

        statement.setString(1, key);

        try (var rs = statement.executeQuery()) {

            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void producerEnqueuesGcMessageForEveryBackfilledRow() throws SQLException {

        assertThat(scalar("SELECT count(*) FROM pgmq.q_upload_gc")).isEqualTo(20L);

        // Delay 86400s: chưa row nào visible sớm hơn 24h.
        var visibleNow = scalar("""
            SELECT count(*) FROM pgmq.q_upload_gc WHERE vt <= now()
            """);

        assertThat(visibleNow).isZero();
    }

    @Test
    void newUploadRowIsEnqueuedWithUploadIdAndKey() throws SQLException {

        var id = UUID.randomUUID();

        try (var connection = connection();
            var statement = connection.prepareStatement(
                "INSERT INTO upload (id, s3_key, content_type) VALUES (?, ?, 'image/jpeg')")) {

            statement.setObject(1, id);
            statement.setString(2, "newly-inserted.jpeg");
            statement.executeUpdate();
        }

        try (var connection = connection();
            var statement = connection.prepareStatement("""
                SELECT message ->> 'upload_id', vt > now() AS delayed
                FROM pgmq.q_upload_gc WHERE message ->> 's3_key' = 'newly-inserted.jpeg'
                """);
            var rs = statement.executeQuery()) {

            assertThat(rs.next()).isTrue();
            assertThat(UUID.fromString(rs.getString(1))).isEqualTo(id);
            assertThat(rs.getBoolean(2)).isTrue();
        }

        // Dọn sạch để các test khác đếm số message không bị nhiễu bất kể thứ tự chạy.
        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute(
                "DELETE FROM pgmq.q_upload_gc WHERE message ->> 's3_key' = 'newly-inserted.jpeg'");
            statement.execute("DELETE FROM upload WHERE s3_key = 'newly-inserted.jpeg'");
        }
    }

    @Test
    void uploadGcQueueIsRegistered() throws SQLException {

        assertThat(scalar(
            "SELECT count(*) FROM pgmq.meta WHERE queue_name = 'upload_gc'")).isEqualTo(1L);
    }
}
