package com.minhnbnt.fashionstore.features.chat;

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
 * Migration test V42 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V8 (chat_message.image_key còn nguyên), migrate qua V37 (backfill upload)
 * rồi V42 (đổi {@code image_upload_id}), khẳng định data cũ map đúng sang FK, tin nhắn
 * không ảnh giữ NULL, cột key bị drop và FK chặn xoá upload đang được tham chiếu.
 */
@Testcontainers
class ChatMessageUploadMigrationTest {

    private static final String IMAGE_KEY = "e1111111-1111-1111-1111-111111111111.webp";

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SHOP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID MESSAGE_WITH_IMAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID TEXT_ONLY_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("ghcr.io/minhnbnt/postgresql:latest")
            .asCompatibleSubstituteFor("postgres"))
        .withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll
    static void migrateSeedAndMigrateAgain() throws SQLException {

        migrateTo(MigrationVersion.fromVersion("8"));
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
                VALUES ('%s', 'legacy-buyer@t.local', 'Legacy Buyer')
                """.formatted(BUYER_ID));

            statement.execute("""
                INSERT INTO shop (id, name, slug)
                VALUES ('%s', 'Legacy Shop', 'legacy-shop')
                """.formatted(SHOP_ID));

            statement.execute("""
                INSERT INTO chat_conversation (id, shop_id, buyer_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(CONVERSATION_ID, SHOP_ID, BUYER_ID));

            // Tin nhắn có ảnh đính kèm.
            statement.execute("""
                INSERT INTO chat_message (id, conversation_id, sender_id, sender_type, content, image_key)
                VALUES ('%s', '%s', '%s', 'BUYER', 'Xem giúp tôi cái này', '%s')
                """.formatted(MESSAGE_WITH_IMAGE_ID, CONVERSATION_ID, BUYER_ID, IMAGE_KEY));

            // Tin nhắn chỉ có chữ — cột image_key NULL.
            statement.execute("""
                INSERT INTO chat_message (id, conversation_id, sender_id, sender_type, content)
                VALUES ('%s', '%s', '%s', 'SELLER', 'Dạ còn hàng nhé bạn')
                """.formatted(TEXT_ONLY_MESSAGE_ID, CONVERSATION_ID, BUYER_ID));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnIsDroppedAndFkColumnAdded() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var columns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'chat_message'
                  AND (column_name LIKE '%\\_key' OR column_name LIKE '%\\_upload\\_id')
                """)) {

            var names = new StringBuilder();
            while (columns.next()) {
                names.append(columns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("image_key");
            assertThat(names.toString()).contains("image_upload_id");
        }
    }

    @Test
    void legacyKeyMapsToBackfilledUploadRow() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT u.s3_key FROM chat_message t JOIN upload u ON u.id = t.image_upload_id
            WHERE t.id = ?
            """)) {

            statement.setObject(1, MESSAGE_WITH_IMAGE_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(IMAGE_KEY);
            }
        }
    }

    @Test
    void textOnlyMessageKeepsNullImage() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement(
            "SELECT image_upload_id FROM chat_message WHERE id = ?")) {

            statement.setObject(1, TEXT_ONLY_MESSAGE_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("image_upload_id")).isNull();
            }
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        assertThatThrownBy(() -> {
            try (var connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DELETE FROM upload WHERE s3_key = '" + IMAGE_KEY + "'");
            }
        }).isInstanceOf(SQLException.class);
    }
}
