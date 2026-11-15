package com.minhnbnt.fashionstore;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.minhnbnt.fashionstore.infrastructure.seed.DemoImageFetcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

    /**
     * Container Postgres dùng chung cho cả JVM — cố ý KHÔNG đăng ký làm bean: Spring Boot 4
     * ({@code TestcontainersLifecycleBeanPostProcessor}) gọi {@code close()} trên mọi bean
     * {@code Startable} khi một context bị destroy, kể cả với {@code destroyMethod = ""} —
     * một context fail boot sẽ kill container dùng chung và làm nổ dây chuyền
     * {@code ConnectException} cho mọi context còn lại (JUnit chạy class song song).
     * Static + synchronized giữ đúng một container cho cả JVM; JVM thoát thì JVM-hook
     * reaper của Testcontainers dọn.
     */
    public static synchronized PostgreSQLContainer postgresContainer() {

        if (sharedPostgres == null) {
            sharedPostgres = startedPostgres();
        }

        return sharedPostgres;
    }

    /** Tương tự {@link #postgresContainer()}: container sống theo JVM, không theo vòng đời context. */
    public static synchronized GenericContainer<?> s3Container() {

        if (sharedS3 == null) {
            sharedS3 = startedS3();
        }

        return sharedS3;
    }

    /**
     * Database rỗng mới trên container Postgres dùng chung, cho test cần schema trắng để tự
     * dựng từ đầu (migration tests): thay vì mỗi class tự dựng một container — chết giữa chừng
     * khi nhiều container start song song làm podman quá tải — tạo database chỉ tốn ~100ms.
     */
    public static IsolatedDatabase isolatedDatabase() {

        var container = postgresContainer();
        var database = createEmptyDatabase(container);

        var base = container.getJdbcUrl();
        var slash = base.lastIndexOf('/');
        var jdbcUrl = base.substring(0, slash + 1) + database;

        return new IsolatedDatabase(jdbcUrl, container.getUsername(), container.getPassword());
    }

    /** Thông tin truy cập một database rỗng trên container Postgres dùng chung. */
    public record IsolatedDatabase(String jdbcUrl, String username, String password) {}

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
    DynamicPropertyRegistrar s3Properties() {

        // Bucket riêng cho từng context: S3Config.ensureBucketExists sẽ tự tạo lúc boot,
        // tránh data chéo giữa các context chạy song song.
        var bucket = "fashionstore-test-" + UUID.randomUUID().toString().replace("-", "");
        var container = s3Container();

        return registry -> {
            registry.add("s3.endpoint", () -> endpointOf(container));
            registry.add("s3.public-base-url", () -> endpointOf(container));
            registry.add("s3.access-key", () -> S3_ACCESS_KEY);
            registry.add("s3.secret-key", () -> S3_SECRET_KEY);
            registry.add("s3.bucket", () -> bucket);
        };
    }

    /**
     * Pool connection nhỏ cho từng context test: Hikari mặc định mở tới 10 connection/context,
     * trong khi cache của Spring giữ tối đa 32 context cùng bám vào MỘT container postgres dùng
     * chung (mỗi context một database) — đỉnh điểm hơn 300 connection, vượt max_connections=100
     * của postgres. Test trong một context chạy tuần tự (same_thread), 2 connection là đủ cho
     * test method và job định kỳ chạy chen vào.
     */
    @Bean
    DynamicPropertyRegistrar datasourceProperties() {
        return registry -> registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return new PerContextConnectionDetails(postgresContainer());
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

    private static final class PerContextConnectionDetails implements JdbcConnectionDetails {

        private final PostgreSQLContainer container;

        private final String database;

        private PerContextConnectionDetails(PostgreSQLContainer container) {
            this.container = container;
            this.database = createEmptyDatabase(container);
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
    }

    private static String createEmptyDatabase(PostgreSQLContainer container) {

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
