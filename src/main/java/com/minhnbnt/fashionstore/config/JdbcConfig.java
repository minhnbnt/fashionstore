package com.minhnbnt.fashionstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;

@Configuration
public class JdbcConfig {

  @Bean
  public JdbcPostgresDialect jdbcDialect() {
    return JdbcPostgresDialect.INSTANCE;
  }
}
