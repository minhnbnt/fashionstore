package com.minhnbnt.fashionstore.features.catalog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V46: extension vectorscale, bảng product_embedding, queue embed_jobs và trigger
 * producer trên product_image. Backfill ảnh cũ, cascade khi xoá ảnh, SET NULL khi xoá upload.
 */
class ProductEmbeddingMigrationTest {

    private static final TestcontainersConfiguration.IsolatedDatabase DB =
        TestcontainersConfiguration.isolatedDatabase();

    private static final UUID SHOP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private static final UUID UPLOAD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    private static final UUID IMAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f4");

    @BeforeAll
    static void seedAtV45ThenMigrate() throws SQLException {

        baseFlyway().target(MigrationVersion.fromVersion("45")).load().migrate();

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("""
                INSERT INTO shop (id, name, slug) VALUES ('%s', 'Embed Shop', 'embed-shop')
                """.formatted(SHOP_ID));
            statement.execute("""
                INSERT INTO product (id, shop_id, name, slug) VALUES ('%s', '%s', 'P', 'p-embed')
                """.formatted(PRODUCT_ID, SHOP_ID));
            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'seed.jpg', 'image/jpeg')
                """.formatted(UPLOAD_ID));
            statement.execute("""
                INSERT INTO product_image (id, product_id, upload_id) VALUES ('%s', '%s', '%s')
                """.formatted(IMAGE_ID, PRODUCT_ID, UPLOAD_ID));
        }

        baseFlyway().load().migrate();
    }

    private static FluentConfiguration baseFlyway() {

        return Flyway.configure()
            .dataSource(DB.jdbcUrl(), DB.username(), DB.password())
            .locations("classpath:db/migration")
            .outOfOrder(true);
    }

    private static Connection connection() throws SQLException {

        return DriverManager.getConnection(DB.jdbcUrl(), DB.username(), DB.password());
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
    void extensionsQueueTableAndIndexExist() throws SQLException {

        assertThat(scalar("SELECT count(*) FROM pg_extension WHERE extname = 'vectorscale'")).isEqualTo(1L);
        assertThat(scalar("SELECT count(*) FROM pg_extension WHERE extname = 'vector'")).isEqualTo(1L);
        assertThat(scalar("SELECT count(*) FROM pgmq.meta WHERE queue_name = 'embed_jobs'")).isEqualTo(1L);
        assertThat(scalar("SELECT count(*) FROM pg_indexes WHERE indexname = 'product_embedding_diskann'"))
            .isEqualTo(1L);
        assertThat(scalar("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_name = 'product_embedding'
            """)).isEqualTo(1L);
    }

    @Test
    void migrationBackfillsExistingImages() throws SQLException {

        assertThat(scalar("""
            SELECT count(*) FROM pgmq.q_embed_jobs
            WHERE message ->> 'product_image_id' = '%s'
            """.formatted(IMAGE_ID))).isEqualTo(1L);
    }

    @Test
    void insertImageEnqueuesFullPayload() throws SQLException {

        var imageId = UUID.randomUUID();
        var uploadId = UUID.randomUUID();

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'new.jpg', 'image/jpeg')
                """.formatted(uploadId));
            statement.execute("""
                INSERT INTO product_image (id, product_id, upload_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(imageId, PRODUCT_ID, uploadId));
        }

        try (var connection = connection(); var statement = connection.createStatement();
            var rs = statement.executeQuery("""
                SELECT message ->> 'product_id', message ->> 'upload_id', message ->> 's3_key'
                FROM pgmq.q_embed_jobs WHERE message ->> 'product_image_id' = '%s'
                """.formatted(imageId))) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(PRODUCT_ID.toString());
            assertThat(rs.getString(2)).isEqualTo(uploadId.toString());
            assertThat(rs.getString(3)).isEqualTo("new.jpg");
        }
    }

    @Test
    void changingUploadEnqueuesAgainButSortOrderDoesNot() throws SQLException {

        var imageId = UUID.randomUUID();
        var firstUpload = UUID.randomUUID();

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'one.jpg', 'image/jpeg')
                """.formatted(firstUpload));
            statement.execute("""
                INSERT INTO product_image (id, product_id, upload_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(imageId, PRODUCT_ID, firstUpload));
            statement.execute("""
                UPDATE product_image SET sort_order = 9 WHERE id = '%s'
                """.formatted(imageId));
        }

        assertThat(countMessages(imageId)).isEqualTo(1L);

        var secondUpload = UUID.randomUUID();

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'two.jpg', 'image/jpeg')
                """.formatted(secondUpload));
            statement.execute("""
                UPDATE product_image SET upload_id = '%s' WHERE id = '%s'
                """.formatted(secondUpload, imageId));
        }

        assertThat(countMessages(imageId)).isEqualTo(2L);
    }

    @Test
    void deletingImageCascadesEmbeddingAndDeletingUploadSetsNull() throws SQLException {

        var imageId = UUID.randomUUID();
        var uploadId = UUID.randomUUID();
        var spareUpload = UUID.randomUUID();

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'keep.jpg', 'image/jpeg')
                """.formatted(uploadId));
            statement.execute("""
                INSERT INTO upload (id, s3_key, content_type) VALUES ('%s', 'spare.jpg', 'image/jpeg')
                """.formatted(spareUpload));
            statement.execute("""
                INSERT INTO product_image (id, product_id, upload_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(imageId, PRODUCT_ID, uploadId));
            statement.execute("""
                INSERT INTO product_embedding (product_image_id, product_id, upload_id, embedding)
                VALUES ('%s', '%s', '%s', array_fill(0.0::float4, ARRAY[512])::vector)
                """.formatted(imageId, PRODUCT_ID, uploadId));
            // Rời ảnh sang upload khác để FK product_image không giữ upload cũ.
            statement.execute("""
                UPDATE product_image SET upload_id = '%s' WHERE id = '%s'
                """.formatted(spareUpload, imageId));
            statement.execute("DELETE FROM upload WHERE id = '%s'".formatted(uploadId));
        }

        assertThat(scalar("""
            SELECT count(*) FROM product_embedding
            WHERE product_image_id = '%s' AND upload_id IS NULL
            """.formatted(imageId))).isEqualTo(1L);

        try (var connection = connection(); var statement = connection.createStatement()) {

            statement.execute("DELETE FROM product_image WHERE id = '%s'".formatted(imageId));
        }

        assertThat(scalar("""
            SELECT count(*) FROM product_embedding WHERE product_image_id = '%s'
            """.formatted(imageId))).isZero();
    }

    private static long countMessages(UUID imageId) throws SQLException {

        return scalar("""
            SELECT count(*) FROM pgmq.q_embed_jobs WHERE message ->> 'product_image_id' = '%s'
            """.formatted(imageId));
    }
}
