package com.minhnbnt.fashionstore;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static @Nullable PostgreSQLContainer sharedPostgres;

    @Bean
    PostgreSQLContainer postgresContainer() {

        synchronized (TestcontainersConfiguration.class) {

            if (sharedPostgres == null) {
                sharedPostgres = startedPostgres();
            }

            return sharedPostgres;
        }
    }

    private static PostgreSQLContainer startedPostgres() {

        var image = DockerImageName
            .parse("ghcr.io/minhnbnt/postgresql:latest")
            .asCompatibleSubstituteFor("postgres");

        var container = new PostgreSQLContainer(image);
        container.start();

        return container;
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(PostgreSQLContainer container) {
        return new PerContextConnectionDetails(container);
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
