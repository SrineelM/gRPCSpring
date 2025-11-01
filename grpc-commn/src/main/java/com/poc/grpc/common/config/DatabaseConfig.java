package com.poc.grpc.order.config;

import jakarta.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Configuration class for environment-specific database beans.
 * <p>
 * This class defines DataSource beans that are activated based on the active Spring profile
 * and specific application properties. It allows for easy switching between different
 * database setups, such as an in-memory H2 database for local development and a
 * persistent PostgreSQL database for other environments.
 * <p>
 * Note: The package is 'order.config' but the file path in context was 'user.config'.
 * Ensure package name consistency across the project structure.
 */
@Configuration
public class DatabaseConfig {

    /**
     * Creates an in-memory H2 DataSource bean for local development and testing.
     * <p>
     * This bean is only activated under two specific conditions:
     * 1. The 'local' Spring profile is active (via {@code @Profile("local")}).
     * 2. The application property 'spring.datasource.platform' is set to 'h2'
     *    (via {@code @ConditionalOnProperty}).
     * <p>
     * This allows developers to quickly run the application without needing to set up
     * an external database like PostgreSQL. The database is initialized with a schema
     * and pre-populated with test data from the specified SQL scripts.
     *
     * @return A configured H2 embedded DataSource.
     */
    @Bean
    @Profile("local")
    @ConditionalOnProperty(name = "spring.datasource.platform", havingValue = "h2")
    public DataSource h2DataSource() {
        return new EmbeddedDatabaseBuilder()
                // Specify the type of embedded database to use.
                .setType(EmbeddedDatabaseType.H2)
                // Set a name for the in-memory database.
                .setName("orderdb")
                // Execute the schema creation script on startup.
                .addScript("classpath:schema-h2.sql")
                // Execute the data population script after schema creation.
                .addScript("classpath:data-h2.sql")
                .build();
    }
}