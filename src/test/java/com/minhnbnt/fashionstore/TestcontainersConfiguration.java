package com.minhnbnt.fashionstore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		var image = DockerImageName.parse("ghcr.io/minhnbnt/postgresql:latest")
			.asCompatibleSubstituteFor("postgres");
		return new PostgreSQLContainer(image);
	}
}
