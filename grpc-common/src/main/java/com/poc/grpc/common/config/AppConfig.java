package com.poc.grpc.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;

/**
 * Common Application Configuration
 *
 * <p>This configuration class imports and configures common components shared across all
 * microservices in the system.
 *
 * <p>Features: 1. Database configuration 2. Security setup 3. Redis caching 4. gRPC interceptors
 *
 * <p>Imported Configurations: - DatabaseConfig: Database connection and JPA setup - SecurityConfig:
 * JWT and authentication - RedisConfig: Caching infrastructure - GrpcClientConfig: gRPC client
 * setup
 */
@Slf4j
@Configuration
@EnableJpaAuditing // Enables automatic population of fields like @CreatedDate and @LastModifiedDate
@EnableAsync // Enables support for asynchronous method execution with @Async
@Import({DatabaseConfig.class, SecurityConfig.class, RedisConfig.class, GrpcClientConfig.class})
public class AppConfig {

  // Injected Environment to access application properties and profiles
  @Autowired private Environment environment;

  /** Initializes the common application configuration, logging the setup process. */
  public AppConfig() {
    log.info("Initializing common application configuration");
    log.debug("Loading shared configurations: Database, Security, Redis, gRPC");
  }

  // --- Application Properties ---

  /** The timezone used for the application clock, defaults to "UTC". */
  @Value("${app.timezone:UTC}")
  private String applicationTimezone;

  /** The core number of threads for the general-purpose async task executor. */
  @Value("${app.async.core-pool-size:5}")
  private int asyncCorePoolSize;

  /** The maximum number of threads for the general-purpose async task executor. */
  @Value("${app.async.max-pool-size:10}")
  private int asyncMaxPoolSize;

  /** The queue capacity for the general-purpose async task executor. */
  @Value("${app.async.queue-capacity:25}")
  private int asyncQueueCapacity;

  /** The core number of threads for the Saga orchestration async task executor. */
  @Value("${app.saga.async.core-pool-size:3}")
  private int sagaAsyncCorePoolSize;

  /** The maximum number of threads for the Saga orchestration async task executor. */
  @Value("${app.saga.async.max-pool-size:8}")
  private int sagaAsyncMaxPoolSize;

  /** The queue capacity for the Saga orchestration async task executor. */
  @Value("${app.saga.async.queue-capacity:50}")
  private int sagaAsyncQueueCapacity;

  /**
   * Provides a primary, application-wide Jackson ObjectMapper for consistent JSON processing. This
   * configuration ensures that all JSON serialization and deserialization follows defined standards
   * (snake_case, proper Java 8 time handling, etc.).
   *
   * @return A configured ObjectMapper instance.
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    log.info("Configuring Jackson ObjectMapper with custom settings.");
    ObjectMapper mapper = new ObjectMapper();

    // Register module to correctly handle Java 8 Date/Time types (e.g., LocalDate, LocalDateTime)
    mapper.registerModule(new JavaTimeModule());

    // Configure property naming strategy to convert camelCase field names to snake_case in JSON.
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // Serialize dates as ISO-8601 strings (e.g., "2023-10-27T10:00:00Z") instead of numeric
    // timestamps.
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Do not fail when encountering unknown properties in JSON during deserialization.
    // This makes the API more resilient to new fields added by clients.
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Exclude fields with null values from the serialized JSON output to reduce payload size.
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // Enable pretty-printing of JSON for better readability in development environments.
    if (isDevEnvironment()) {
      mapper.enable(SerializationFeature.INDENT_OUTPUT);
      log.info("Development profile active. Enabling pretty-printing for JSON.");
    }

    log.info("Jackson ObjectMapper configured successfully.");
    return mapper;
  }

  /**
   * Provides a Clock bean that is timezone-aware. Using a Clock bean instead of
   * `LocalDateTime.now()` directly allows for easier testing, as the clock can be mocked to provide
   * a fixed time. It also ensures time consistency.
   *
   * @return A Clock instance configured with the application's default timezone.
   */
  @Bean
  public Clock applicationClock() {
    ZoneId zoneId = ZoneId.of(applicationTimezone);
    log.info("Application clock configured with timezone: {}", zoneId);
    return Clock.system(zoneId);
  }

  /**
   * Configures the default thread pool for executing methods annotated with @Async. This executor
   * is used for general-purpose asynchronous tasks.
   *
   * @return A configured Executor for async tasks.
   */
  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    log.info(
        "Configuring async task executor - Core: {}, Max: {}, Queue: {}",
        asyncCorePoolSize,
        asyncMaxPoolSize,
        asyncQueueCapacity);

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(asyncCorePoolSize);
    executor.setMaxPoolSize(asyncMaxPoolSize);
    executor.setQueueCapacity(asyncQueueCapacity);
    executor.setThreadNamePrefix("OrderService-Async-");
    // Ensures that the executor waits for tasks to complete on shutdown.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();

    log.info("Async task executor configured successfully.");
    return executor;
  }

  /**
   * Configures a dedicated thread pool for Saga orchestration tasks. Using a separate executor for
   * Sagas prevents long-running saga steps from starving the general-purpose async task pool,
   * improving resource management and resilience.
   *
   * @return A configured Executor for saga-related async tasks.
   */
  @Bean(name = "sagaTaskExecutor")
  public Executor sagaTaskExecutor() {
    log.info(
        "Configuring saga orchestration task executor - Core: {}, Max: {}, Queue: {}",
        sagaAsyncCorePoolSize,
        sagaAsyncMaxPoolSize,
        sagaAsyncQueueCapacity);

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(sagaAsyncCorePoolSize);
    executor.setMaxPoolSize(sagaAsyncMaxPoolSize);
    executor.setQueueCapacity(sagaAsyncQueueCapacity);
    executor.setThreadNamePrefix("Saga-Orchestrator-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    // A longer termination wait time is allocated for potentially complex saga compensation logic.
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();

    log.info("Saga task executor configured successfully.");
    return executor;
  }

  /**
   * Provides a primary JSR-303 Validator bean for use across the application. This ensures
   * consistent validation logic (e.g., in @Valid annotations in controllers).
   *
   * @return A Validator instance.
   */
  @Bean
  @Primary
  public Validator validator() {
    log.info("Configuring bean validator.");
    return new LocalValidatorFactoryBean();
  }

  /**
   * Provides a RestTemplate bean for making synchronous HTTP client calls. It is pre-configured
   * with connection and read timeouts to prevent application threads from hanging on slow network
   * responses.
   *
   * @return A configured RestTemplate instance.
   */
  @Bean
  public RestTemplate restTemplate() {
    log.info("Configuring RestTemplate for HTTP client calls.");

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    // Timeout for establishing a connection (in milliseconds)
    requestFactory.setConnectTimeout(5000);
    // Timeout for reading data from a connection (in milliseconds)
    requestFactory.setReadTimeout(10000);

    return new RestTemplate(requestFactory);
  }

  /**
   * Provides a utility bean for generating unique correlation IDs. These IDs are useful for tracing
   * requests as they flow through different services and components.
   *
   * @return A CorrelationIdGenerator instance.
   */
  @Bean
  public CorrelationIdGenerator correlationIdGenerator() {
    return new CorrelationIdGenerator();
  }

  /**
   * Checks if the "dev" Spring profile is active. This is a utility method used to enable
   * development-only features, such as pretty-printing JSON.
   *
   * @return true if the 'dev' profile is active, false otherwise.
   */
  private boolean isDevEnvironment() {
    return this.environment.acceptsProfiles(Profiles.of("dev"));
  }

  // --- Nested Utility and Event Classes ---

  /**
   * A simple utility class for generating UUID-based correlation IDs. Defined as a static nested
   * class because it doesn't need access to AppConfig's instance state.
   */
  public static class CorrelationIdGenerator {
    /**
     * Generates a new random UUID string.
     *
     * @return A unique string identifier.
     */
    public String generate() {
      return UUID.randomUUID().toString();
    }
  }

  /**
   * A custom, domain-specific event publisher that acts as a facade over Spring's underlying
   * ApplicationEventPublisher. This promotes a cleaner, domain-oriented approach to publishing
   * events.
   */
  @Component
  @RequiredArgsConstructor
  public static class CustomApplicationEventPublisher {

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a custom OrderApplicationEvent.
     *
     * @param eventType A string describing the type of event (e.g., "ORDER_CREATED").
     * @param eventData The payload of the event (e.g., the Order object).
     */
    public void publishOrderEvent(String eventType, Object eventData) {
      OrderApplicationEvent event = new OrderApplicationEvent(this, eventType, eventData);
      eventPublisher.publishEvent(event);
      log.debug("Published order event: type='{}', data='{}'", eventType, eventData);
    }
  }

  /**
   * A custom application event class representing events related to the Order domain. It extends
   * Spring's ApplicationEvent and carries a specific type and data payload, allowing for decoupled
   * communication between components.
   */
  public static class OrderApplicationEvent extends ApplicationEvent {
    private final String eventType;
    private final Object eventData;

    /**
     * Constructs a new OrderApplicationEvent.
     *
     * @param source The component that is publishing the event (generally {@code this}).
     * @param eventType A string identifier for the event (e.g., "ORDER_CREATED").
     * @param eventData The data associated with the event (e.g., an Order entity).
     */
    public OrderApplicationEvent(Object source, String eventType, Object eventData) {
      super(source);
      this.eventType = eventType;
      this.eventData = eventData;
    }

    public String getEventType() {
      return eventType;
    }

    public Object getEventData() {
      return eventData;
    }
  }
}
