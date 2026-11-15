package com.minhnbnt.fashionstore.features.review;

import java.sql.Connection;
import java.sql.DriverManager;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration test V43 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V36 (review.image_keys[] + video_key còn nguyên), migrate qua V37
 * (backfill upload từ key) rồi V43 (tách review_media), khẳng định ảnh map thành row
 * IMAGE với sort_order theo vị trí trong mảng, video thành row VIDEO xếp sau, review
 * không media không có row, cột key bị drop và FK chặn xoá upload đang được tham chiếu.
 */
@Testcontainers
class ReviewMediaUploadMigrationTest {

    private static final String IMAGE_KEY_1 = "e2111111-1111-1111-1111-111111111111.webp";

    private static final String IMAGE_KEY_2 = "e2111112-2222-2222-2222-222222222222.webp";

    private static final String VIDEO_KEY = "e2111113-3333-3333-3333-333333333333.mp4";

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static final UUID SHOP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static final UUID REVIEW_WITH_MEDIA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f4");

    private static final UUID REVIEW_TEXT_ONLY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f5");

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
                INSERT INTO user_profile (id, email, display_name)
                VALUES ('%s', 'legacy-reviewer@t.local', 'Legacy Reviewer')
                """.formatted(BUYER_ID));

            statement.execute("""
                INSERT INTO shop (id, name, slug)
                VALUES ('%s', 'Legacy Shop', 'legacy-review-shop')
                """.formatted(SHOP_ID));

            statement.execute("""
                INSERT INTO product (id, shop_id, name, slug)
                VALUES ('%s', '%s', 'Legacy Ao', 'legacy-review-ao')
                """.formatted(PRODUCT_ID, SHOP_ID));

            // Review có 2 ảnh + 1 video.
            statement.execute("""
                INSERT INTO review (id, product_id, buyer_id, buyer_display_name, stars, image_keys, video_key)
                VALUES ('%s', '%s', '%s', 'Legacy Reviewer', 5, '{%s,%s}', '%s')
                """.formatted(REVIEW_WITH_MEDIA_ID, PRODUCT_ID, BUYER_ID, IMAGE_KEY_1, IMAGE_KEY_2, VIDEO_KEY));

            // Review chỉ có chữ — image_keys rỗng, video_key NULL.
            statement.execute("""
                INSERT INTO review (id, product_id, buyer_id, buyer_display_name, stars)
                VALUES ('%s', '%s', '%s', 'Legacy Reviewer', 4)
                """.formatted(REVIEW_TEXT_ONLY_ID, PRODUCT_ID, BUYER_ID));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnsAreDroppedAndReviewMediaCreated() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var columns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'review' AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_keys')
                """)) {

            var names = new StringBuilder();
            while (columns.next()) {
                names.append(columns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("image_keys").doesNotContain("video_key");
        }
    }

    @Test
    void imageKeysBackfillInArrayOrder() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT u.s3_key, rm.sort_order FROM review_media rm
            JOIN upload u ON u.id = rm.upload_id
            WHERE rm.review_id = ? AND rm.kind = 'IMAGE'
            ORDER BY rm.sort_order
            """)) {

            statement.setObject(1, REVIEW_WITH_MEDIA_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(IMAGE_KEY_1);
                assertThat(rs.getInt(2)).isZero();

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(IMAGE_KEY_2);
                assertThat(rs.getInt(2)).isEqualTo(1);

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void videoBackfillsAsVideoKindAfterImages() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT u.s3_key, rm.sort_order FROM review_media rm
            JOIN upload u ON u.id = rm.upload_id
            WHERE rm.review_id = ? AND rm.kind = 'VIDEO'
            """)) {

            statement.setObject(1, REVIEW_WITH_MEDIA_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(VIDEO_KEY);
                // Xếp sau 2 ảnh.
                assertThat(rs.getInt(2)).isEqualTo(2);
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void textOnlyReviewHasNoMediaRows() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM review_media WHERE review_id = ?")) {

            statement.setObject(1, REVIEW_TEXT_ONLY_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        assertThatThrownBy(() -> {
            try (var connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DELETE FROM upload WHERE s3_key = '" + IMAGE_KEY_1 + "'");
            }
        }).isInstanceOf(SQLException.class);
    }
}
