package com.minhnbnt.fashionstore.features.order;

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
 * Migration test V44 chạy Flyway thật trên image Postgres của project: seed data legacy
 * ở trạng thái V8 (return_request.evidence_keys[] còn nguyên), migrate qua V37
 * (backfill upload từ key) rồi V44 (tách return_evidence), khẳng định file bằng chứng
 * map thành row với sort_order theo vị trí trong mảng, yêu cầu không bằng chứng không có
 * row, cột key bị drop và FK chặn xoá upload đang được tham chiếu.
 */
@Testcontainers
class ReturnEvidenceUploadMigrationTest {

    private static final String EVIDENCE_KEY_1 = "e3111111-1111-1111-1111-111111111111.webp";

    private static final String EVIDENCE_KEY_2 = "e3111112-2222-2222-2222-222222222222.mp4";

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private static final UUID SHOP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private static final UUID RETURN_WITH_EVIDENCE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f6");

    private static final UUID RETURN_NO_EVIDENCE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f7");

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
                VALUES ('%s', 'legacy-returner@t.local', 'Legacy Returner')
                """.formatted(BUYER_ID));

            statement.execute("""
                INSERT INTO shop (id, name, slug)
                VALUES ('%s', 'Legacy Shop', 'legacy-return-shop')
                """.formatted(SHOP_ID));

            statement.execute("""
                INSERT INTO orders (id, code, shop_id, buyer_id, subtotal, shipping_fee, total, shipping_name, shipping_phone, shipping_address)
                VALUES ('%s', 'ORD-V44', '%s', '%s', 100000, 0, 100000, 'Legacy Returner', '0900', 'Ha Noi')
                """.formatted(ORDER_ID, SHOP_ID, BUYER_ID));

            // Yêu cầu trả hàng có 2 file bằng chứng (ảnh + video).
            statement.execute("""
                INSERT INTO return_request (id, order_id, shop_id, buyer_id, reason, evidence_keys)
                VALUES ('%s', '%s', '%s', '%s', 'DAMAGED', '{%s,%s}')
                """.formatted(RETURN_WITH_EVIDENCE_ID, ORDER_ID, SHOP_ID, BUYER_ID, EVIDENCE_KEY_1, EVIDENCE_KEY_2));

            // Yêu cầu không kèm bằng chứng — evidence_keys rỗng.
            statement.execute("""
                INSERT INTO return_request (id, order_id, shop_id, buyer_id, reason)
                VALUES ('%s', '%s', '%s', '%s', 'CHANGE_MIND')
                """.formatted(RETURN_NO_EVIDENCE_ID, ORDER_ID, SHOP_ID, BUYER_ID));
        }
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void keyColumnIsDroppedAndReturnEvidenceCreated() throws SQLException {

        try (var connection = connection(); var statement = connection.createStatement();
            var columns = statement.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'return_request' AND column_name LIKE '%\\_keys'
                """)) {

            var names = new StringBuilder();
            while (columns.next()) {
                names.append(columns.getString(1)).append('\n');
            }

            assertThat(names.toString()).doesNotContain("evidence_keys");
        }
    }

    @Test
    void evidenceKeysBackfillInArrayOrder() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement("""
            SELECT u.s3_key, re.sort_order FROM return_evidence re
            JOIN upload u ON u.id = re.upload_id
            WHERE re.return_request_id = ?
            ORDER BY re.sort_order
            """)) {

            statement.setObject(1, RETURN_WITH_EVIDENCE_ID);

            try (var rs = statement.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(EVIDENCE_KEY_1);
                assertThat(rs.getInt(2)).isZero();

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(EVIDENCE_KEY_2);
                assertThat(rs.getInt(2)).isEqualTo(1);

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void returnWithoutEvidenceHasNoRows() throws SQLException {

        try (var connection = connection(); var statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM return_evidence WHERE return_request_id = ?")) {

            statement.setObject(1, RETURN_NO_EVIDENCE_ID);

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
                statement.execute("DELETE FROM upload WHERE s3_key = '" + EVIDENCE_KEY_1 + "'");
            }
        }).isInstanceOf(SQLException.class);
    }
}
