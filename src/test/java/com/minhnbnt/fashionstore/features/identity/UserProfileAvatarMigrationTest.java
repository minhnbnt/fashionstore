package com.minhnbnt.fashionstore.features.identity;

import java.sql.Connection;
import java.sql.DriverManager;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration test V38 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V36 ({@code avatar_key} còn nguyên), migrate qua V37 (backfill upload)
 * rồi V38 (đổi {@code avatar_upload_id}), khẳng định data cũ map đúng sang FK, cột key
 * bị drop và FK chặn xoá upload đang được tham chiếu (design D5 + D6).
 */
@Testcontainers
class UserProfileAvatarMigrationTest {

    private static final String AVATAR_KEY = "a1111111-1111-1111-1111-111111111111.png";

    private static final UUID USER_WITH_AVATAR =
        UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static final UUID USER_WITHOUT_AVATAR =
        UUID.fromString("00000000-0000-0000-0000-0000000000a2");

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
                VALUES ('%s', 'legacy-avatar@t.local', 'Avatar Buyer', '%s'),
                       ('%s', 'legacy-plain@t.local', 'Plain Buyer', NULL)
                """.formatted(USER_WITH_AVATAR, AVATAR_KEY, USER_WITHOUT_AVATAR));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void avatarKeyColumnIsDroppedAndFkColumnAdded() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement()) {

            try (var rs = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'user_profile' AND column_name IN ('avatar_key', 'avatar_upload_id')
                """)) {

                var names = new StringBuilder();
                while (rs.next()) {
                    names.append(rs.getString(1)).append('\n');
                }

                assertThat(names.toString()).contains("avatar_upload_id").doesNotContain("avatar_key");
            }
        }
    }

    @Test
    void legacyAvatarMapsToBackfilledUploadRow() throws SQLException {

        try (var connection = connection();
            var statement = connection.prepareStatement("""
                SELECT u.s3_key FROM user_profile p
                JOIN upload u ON u.id = p.avatar_upload_id
                WHERE p.id = ?
                """)) {

            statement.setObject(1, USER_WITH_AVATAR);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(AVATAR_KEY);
            }
        }
    }

    @Test
    void nullAvatarStaysNull() throws SQLException {

        try (var connection = connection();
            var statement = connection.prepareStatement(
                "SELECT avatar_upload_id FROM user_profile WHERE id = ?")) {

            statement.setObject(1, USER_WITHOUT_AVATAR);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1)).isNull();
            }
        }
    }

    @Test
    void fkBlocksDeletingReferencedUpload() {

        // "Vệ sĩ" (design D5): consumer GC xoá upload đang bị tham chiếu phải nổ constraint.
        assertThatThrownBy(() -> {
            try (var connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DELETE FROM upload WHERE s3_key = '" + AVATAR_KEY + "'");
            }
        }).isInstanceOf(SQLException.class);
    }
}
