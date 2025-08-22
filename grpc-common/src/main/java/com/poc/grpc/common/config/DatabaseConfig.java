package com.poc.grpc.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database Configuration
 *
 * <p>This configuration class sets up the database connection and JPA infrastructure. It provides
 * environment-specific database configurations and connection pooling.
 *
 * <p>Features: 1. H2/Postgres database support 2. Connection pooling with HikariCP 3. JPA
 * configuration with Hibernate 4. Transaction management
 *
 * <p>Environment-specific Settings: - Local: H2 in-memory database - Dev/QA: H2 or Postgres - Prod:
 * Postgres with SSL
 */
@Slf4j
@Configuration
@EnableJpaRepositories(
    basePackages = "com.poc.grpc",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager")
@EnableTransactionManagement
@RequiredArgsConstructor
public class DatabaseConfig {

  private final Environment env;
  private final DataSourceProperties dataSourceProperties;

  /**
   * Configures the primary data source with HikariCP connection pooling. Uses environment-specific
   * settings for optimal performance.
   *
   * @return The configured data source
   */
  @Bean
  @Primary
  public DataSource dataSource() {
    log.info("Configuring database connection pool");

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dataSourceProperties.getUrl());
    config.setUsername(dataSourceProperties.getUsername());
    config.setPassword(dataSourceProperties.getPassword());
    config.setDriverClassName(dataSourceProperties.getDriverClassName());

    // Connection pool settings
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(5);
    config.setIdleTimeout(300000);
    config.setConnectionTimeout(20000);
    config.setMaxLifetime(1200000);

    // Enable SSL for production
    if (env.matchesProfiles("prod")) {
      log.info("Enabling SSL for database connection");
      config.addDataSourceProperty("ssl", true);
      config.addDataSourceProperty("sslmode", "verify-full");
    }

    log.debug(
        "Database connection properties - URL: {}, Username: {}",
        dataSourceProperties.getUrl(),
        dataSourceProperties.getUsername());

    return new HikariDataSource(config);
  }

  /**
   * Configures the JPA entity manager factory with Hibernate settings. Sets up dialect, DDL
   * behavior, and other JPA properties.
   *
   * @param dataSource The configured data source
   * @return The entity manager factory
   */
  @Bean
  @Primary
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
    log.info("Configuring JPA entity manager factory");

    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setPackagesToScan("com.poc.grpc");

    HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);

    Map<String, Object> properties = new HashMap<>();
    properties.put(
        "hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto", "none"));
    properties.put("hibernate.dialect", env.getProperty("spring.jpa.properties.hibernate.dialect"));
    properties.put("hibernate.show_sql", env.getProperty("spring.jpa.show-sql", "false"));
    properties.put(
        "hibernate.format_sql",
        env.getProperty("spring.jpa.properties.hibernate.format_sql", "false"));

    em.setJpaPropertyMap(properties);

    log.debug(
        "Hibernate properties configured - DDL Auto: {}, Show SQL: {}",
        properties.get("hibernate.hbm2ddl.auto"),
        properties.get("hibernate.show_sql"));

    return em;
  }

  /**
   * Configures the JPA transaction manager. Enables declarative transaction management
   * with @Transactional.
   *
   * @param entityManagerFactory The configured entity manager factory
   * @return The transaction manager
   */
  @Bean
  @Primary
  public PlatformTransactionManager transactionManager(
      LocalContainerEntityManagerFactoryBean entityManagerFactory) {
    log.info("Configuring JPA transaction manager");
    return new JpaTransactionManager(entityManagerFactory.getObject());
  }
}
