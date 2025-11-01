package com.poc.grpc.common.config;

import com.poc.grpc.common.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Centralized configuration for web security using Spring Security.
 *
 * <p>This class configures the security filter chain for HTTP requests, defining authentication and
 * authorization rules, session management, CORS, and password encoding. It is designed for a
 * stateless, token-based authentication mechanism (JWT).
 *
 * <p>Note: This configuration applies to HTTP endpoints. gRPC security is handled separately by
 * gRPC interceptors (e.g., {@code JwtAuthenticationInterceptor}).
 */
// This configuration is only enabled if the property 'app.jwt.enabled' is set to 'true'.
// This allows for easily disabling JWT-based security in certain environments or for testing.
@ConditionalOnProperty(name = "app.jwt.enabled", havingValue = "true", matchIfMissing = false)
@Configuration
@EnableWebSecurity // Enables Spring Security's web security support.
@EnableMethodSecurity(
    prePostEnabled =
        true) // Enables method-level security annotations like @PreAuthorize("hasRole('ADMIN')").
@RequiredArgsConstructor // Injects final fields via constructor.
public class SecurityConfig {

  // The custom filter that intercepts requests, validates the JWT, and populates the
  // SecurityContext if the token is valid.
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  /**
   * Provides a PasswordEncoder bean for securely hashing and verifying passwords.
   *
   * @return A {@link BCryptPasswordEncoder} instance. BCrypt is the industry standard for password
   *     hashing because it is adaptive and slow by design. The strength factor of 12 is a strong,
   *     recommended value.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Defines the primary security filter chain that applies to all HTTP requests.
   *
   * @param http The {@link HttpSecurity} object to configure.
   * @return The configured {@link SecurityFilterChain}.
   * @throws Exception if an error occurs during configuration.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // Disable CSRF protection, as it's not needed for stateless REST APIs that use tokens for
        // auth.
        .csrf(csrf -> csrf.disable())

        // Apply the custom CORS configuration.
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // Configure session management to be STATELESS. This tells Spring Security not to create
        // or use HTTP sessions, which is essential for a stateless JWT-based architecture.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // This is the entry point for configuring authorization rules.
        // Rules are evaluated in the order they are declared.
        .authorizeHttpRequests(
            authz ->
                authz
                    // Permit public access to basic health and info actuator endpoints.
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    // Secure all other actuator endpoints, requiring an ADMIN role.
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")
                    // Permit public access to authentication-related endpoints (e.g., login,
                    // register).
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    // Require authentication for any other request.
                    .anyRequest()
                    .authenticated())

        // Add the custom JWT filter before the standard UsernamePasswordAuthenticationFilter.
        // This ensures that the JWT is processed and the security context is populated early in the
        // chain.
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

        // Add common security headers to responses to protect against web vulnerabilities.
        .headers(
            headers -> {
              // Prevents the site from being rendered in an <iframe>, protecting against
              // clickjacking.
              headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
              // Enforces HTTPS by telling browsers to only communicate with the site over a secure
              // connection.
              headers.httpStrictTransportSecurity(
                  hstsConfig -> hstsConfig.maxAgeInSeconds(31536000) // 1 year
                  );
            });

    // Builds the SecurityFilterChain, which is then used by Spring Security to handle web security.
    return http.build();
  }

  /**
   * Configures Cross-Origin Resource Sharing (CORS) for the application. This is necessary to allow
   * web frontends hosted on different domains to interact with the API.
   *
   * @return A {@link CorsConfigurationSource} that applies the defined CORS policy to all paths.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // A permissive pattern allowing all origins. For production, this should be restricted
    // to a specific list of frontend domains (e.g., "https://my-app.com").
    configuration.setAllowedOriginPatterns(List.of("*"));
    // Specify the HTTP methods that are allowed from cross-origin requests.
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    // Specify which headers can be included in cross-origin requests.
    configuration.setAllowedHeaders(Arrays.asList("*"));
    // Allow credentials (like cookies or auth tokens) to be sent with cross-origin requests.
    configuration.setAllowCredentials(true);
    // Set the maximum age (in seconds) for which the response to a preflight request (an HTTP
    // OPTIONS request) can be cached by the browser.
    configuration.setMaxAge(3600L);

    // Create a source that applies the CORS configuration to specific URL patterns.
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // Apply this configuration to all URL paths in the application.
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
