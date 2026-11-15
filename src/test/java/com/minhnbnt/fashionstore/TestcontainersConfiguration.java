package com.minhnbnt.fashionstore;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.minhnbnt.fashionstore.infrastructure.seed.DemoImageFetcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** JPEG rỗng (SOI + EOI): seed demo upload ảnh fake, test không phụ thuộc mạng. */
    private static final byte[] EMPTY_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private static final String S3_ACCESS_KEY = "fashionstore";

    private static final String S3_SECRET_KEY = "passwordpassword";

    private static final int S3_PORT = 9000;

    private static @Nullable PostgreSQLContainer sharedPostgres;

    private static @Nullable GenericContainer<?> sharedS3;

    @Bean
    PostgreSQLContainer postgresContainer() {

        synchronized (TestcontainersConfiguration.class) {

            if (sharedPostgres == null) {
                sharedPostgres = startedPostgres();
            }

            return sharedPostgres;
        }
    }

    @Bean
    GenericContainer<?> s3Container() {
        return s3();
    }

    /** Ảnh demo fake, đè {@code PicsumImageFetcher}: seed chạy được nhưng không tải ảnh thật. */
    @Bean
    @Primary
    DemoImageFetcher demoImageFetcher() {
        return url -> Optional.of(EMPTY_JPEG);
    }

    /**
     * Test tự dựng S3 riêng thay vì dùng endpoint trong {@code config/springboot.yml}: test không phụ thuộc
     * versitygw đang chạy ngoài máy, và không ghi rác vào bucket dev.
     */
    @Bean
    DynamicPropertyRegistrar s3Properties(@Qualifier("s3Container") GenericContainer<?> container) {

        return registry -> {
            registry.add("s3.endpoint", () -> endpointOf(container));
            registry.add("s3.public-base-url", () -> endpointOf(container));
            registry.add("s3.access-key", () -> S3_ACCESS_KEY);
            registry.add("s3.secret-key", () -> S3_SECRET_KEY);
            registry.add("s3.bucket", () -> "fashionstore-test");
        };
    }

    private static synchronized GenericContainer<?> s3() {

        if (sharedS3 == null) {
            sharedS3 = startedS3();
        }

        return sharedS3;
    }

    private static String endpointOf(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(S3_PORT);
    }

    private static GenericContainer<?> startedS3() {

        var container = new GenericContainer<>(DockerImageName.parse("ghcr.io/minhnbnt/versitygw:latest"))
            .withExposedPorts(S3_PORT)
            .withEnv("ROOT_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("ROOT_SECRET_KEY", S3_SECRET_KEY)
            .withEnv("VGW_PORT", ":" + S3_PORT)
            .withEnv("VGW_ARGS", "--health /health")
            .withEnv("VGW_BACKEND", "posix")
            .withEnv("VGW_BACKEND_ARGS", "/data/s3")
            .withEnv("VGW_CORS_ALLOW_ORIGIN", "*")
            .waitingFor(
                Wait.forHttp("/health")
                    .forPort(S3_PORT)
                    .withStartupTimeout(Duration.ofMinutes(2))
            );
        container.start();

        return container;
    }

    private static PostgreSQLContainer startedPostgres() {

        var image = DockerImageName
            .parse("ghcr.io/minhnbnt/postgresql:latest")
            .asCompatibleSubstituteFor("postgres");

        // Image init extension mất ~40s trên Docker Desktop Windows, mặc định 60s không đủ khi Gradle chạy song song.
        var container = new PostgreSQLContainer(image)
            .withStartupTimeout(Duration.ofMinutes(3));
        container.start();

        return container;
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return new PerContextConnectionDetails(postgresContainer());
    }

    private static final class PerContextConnectionDetails implements JdbcConnectionDetails {

        private final PostgreSQLContainer container;

        private final String database;

        private PerContextConnectionDetails(PostgreSQLContainer container) {
            this.container = container;
            this.database = createDatabase(container);
        }

        @Override
        public @NonNull String getJdbcUrl() {
            var base = container.getJdbcUrl();
            var slash = base.lastIndexOf('/');
            return base.substring(0, slash + 1) + database;
        }

        @Override
        public @Nullable String getUsername() {
            return container.getUsername();
        }

        @Override
        public @Nullable String getPassword() {
            return container.getPassword();
        }

        private static String createDatabase(PostgreSQLContainer container) {

            var database = "test_" + UUID.randomUUID().toString().replace("-", "");

            try (
                var connection = DriverManager.getConnection(
                    container.getJdbcUrl(),
                    container.getUsername(),
                    container.getPassword()
                );
                var statement = connection.createStatement()
            ) {
                statement.execute("CREATE DATABASE " + database);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to create test database " + database, ex);
            }

            return database;
        }
    }
}
